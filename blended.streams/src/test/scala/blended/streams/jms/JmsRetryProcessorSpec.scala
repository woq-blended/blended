package blended.streams.jms

import java.io.File
import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionFailed}
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, FlowProcessor}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.RichTry._
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

abstract class ProcessorSpecSupport(name : String) extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport {

  protected implicit val timeout : FiniteDuration = 5.seconds
  protected val log : Logger = Logger(getClass().getName())

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  /**
   * Factory for bundles.
   * A `Seq` of bundle name and activator class.
   */
  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  protected implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  protected implicit val materializer : ActorMaterializer = ActorMaterializer()
  protected implicit val ectxt : ExecutionContext = system.dispatcher

  protected val ctCtxt : ContainerContext = mandatoryService[ContainerContext](registry)(None)
  protected val streamsConfig : BlendedStreamsConfig = BlendedStreamsConfig.create(ctCtxt)

  val prefix : String = "spec"

  val brokerName : String = "retry"
  val consumerCount : Int = 5
  val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(prefix = prefix)
  val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)

  val amqCf : IdAwareConnectionFactory =
    mandatoryService[IdAwareConnectionFactory](registry)(Some("(&(vendor=activemq)(provider=activemq))"))

  def producerSettings : String => JmsProducerSettings = destName => JmsProducerSettings(
    log = envLogger,
    headerCfg = headerCfg,
    connectionFactory = amqCf,
    jmsDestination = Some(JmsDestination.create(destName).unwrap)
  )

  // scalastyle:off magic.number
  protected val retryCfg : JmsRetryConfig = JmsRetryConfig(
    cf = amqCf,
    retryDestName = "retryQueue",
    failedDestName = "retryFailed",
    eventDestName = "internal.transactions",
    retryInterval = 500.millis,
    maxRetries = 5,
    retryTimeout = 100.millis,
    headerCfg = headerCfg
  )

  protected val consumeAfter : FiniteDuration = retryCfg.retryInterval * 5
  // scalastyle:on magic.number

  def withExpectedDestination(
    destName : String,
    retryProcessor : JmsRetryProcessor,
    consumeAfter : FiniteDuration,
    completeOn : Seq[FlowEnvelope] => Boolean
  )(env : FlowEnvelope)(assertions : List[FlowEnvelope] => Unit) : Unit = {

    val p : Promise[Unit] = Promise()

    log.info("Starting Retry Processor ...")
    retryProcessor.start()

    sendMessages(producerSettings(retryCfg.retryDestName), envLogger, Seq(env):_*) match {
      case Success(s) =>
        akka.pattern.after(consumeAfter, system.scheduler)(Future {
          // We stop the retry processor, so that it does not process any more messages
          retryProcessor.stop()
          s.shutdown()

          log.info(s"Trying to consume messages from [$destName]")
          p.complete(consumeMessages(destName)(completeOn).map{ env =>
            log.info(s"Applying evaluation function to [${env.size}] envelopes")
            assertions(env)
          })
        })

      case Failure(exception) =>
        // We stop the retry processor, so that it does not process any more messages
        retryProcessor.stop()
        p.failure(exception)
    }

    Await.result(p.future, 10.seconds)
  }

  def consumeMessages(dest : String)(f : Seq[FlowEnvelope] => Boolean) : Try[List[FlowEnvelope]] = Try {

    log.info(s"Consuming messages from [$dest]")
    val coll : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = amqCf,
      dest = JmsDestination.create(dest).get,
      log = envLogger,
      listener = 1,
      completeOn = Some(f),
      timeout = Some(timeout)
    )

    Await.result(coll.result, timeout + 100.millis)
  }

  def consumeTransactions(f : Seq[FlowEnvelope] => Boolean) : Try[List[FlowEnvelope]] =
    consumeMessages("internal.transactions")(f)

}

@RequiresForkedJVM
class JmsRetryProcessorForwardSpec extends ProcessorSpecSupport("retryForward") {

  "Consume messages from the retry destination and reinsert them into the original destination" in {

    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).unwrap

    withExpectedDestination(
      destName = srcQueue,
      retryProcessor = new JmsRetryProcessor(streamsConfig, retryCfg),
      consumeAfter = consumeAfter,
      completeOn = s => s.nonEmpty
    )(retryMsg)(
      _ should not be empty
    )
  }
}

@RequiresForkedJVM
class JmsRetryProcessorRetryCountSpec extends ProcessorSpecSupport("retryCount") {

  "Consume messages from the retry destination and pass them to the retry failed destination if the retry cont exceeds" in {
    val srcQueue: String = "myQueue"

   val retryMsg: FlowEnvelope = FlowEnvelope()
     .withHeader(headerCfg.headerRetrying, "True").unwrap
     .withHeader(headerCfg.headerRetryDestination, srcQueue).unwrap
     .withHeader(headerCfg.headerRetryCount, retryCfg.maxRetries).unwrap

    withExpectedDestination(
      retryCfg.failedDestName,
      new JmsRetryProcessor(streamsConfig, retryCfg),
      consumeAfter = consumeAfter,
      completeOn = _.nonEmpty
    )(retryMsg) { l =>
      l should have size 1

      l.foreach { env =>
        env.header[Long](headerCfg.headerRetryCount) should contain(retryCfg.maxRetries + 1)
      }
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorRetryTimeoutSpec extends ProcessorSpecSupport("retryTimeout") {

  "Consume messages from the retry destination and pass them to the retry failed destination if the retry timeout exceeds" in {
    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).unwrap
      .withHeader(headerCfg.headerFirstRetry, System.currentTimeMillis() - 2 * retryCfg.retryTimeout.toMillis).unwrap

    withExpectedDestination(
      retryCfg.failedDestName,
      new JmsRetryProcessor(streamsConfig, retryCfg),
      consumeAfter = consumeAfter,
      completeOn = _.nonEmpty
    )(retryMsg) { l =>
      l should have size 1
      l.foreach { env =>
        val now : Long = System.currentTimeMillis()
        val first : Long = env.header[Long](headerCfg.headerFirstRetry).getOrElse(now)

        assert(first + retryCfg.retryTimeout.toMillis <= now)
      }
    }

    val otherFailed : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = amqCf,
      dest = JmsQueue(retryCfg.failedDestName),
      log = envLogger,
      listener = 1,
      completeOn = None,
      timeout = Some(timeout)
    )

    Await.result(otherFailed.result, timeout + 500.millis) should be (empty)
  }
}


class JmsRetryProcessorMissingDestinationSpec extends ProcessorSpecSupport("missingDest") {

  "Consume messages from the retry destination and pass them to the retry failed destination if no original destination is known" in {
    val retryMsg : FlowEnvelope = FlowEnvelope()

    withExpectedDestination(
      retryCfg.failedDestName,
      new JmsRetryProcessor(streamsConfig, retryCfg),
      consumeAfter = consumeAfter,
      completeOn = _.nonEmpty
    )(retryMsg){ l =>
      l should have size 1
      l.foreach(_.header[Long](headerCfg.headerRetryCount) should contain (1) )
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorSendToRetrySpec extends ProcessorSpecSupport("sendToRetry") {

  "Reinsert messages into the retry destination if the send to the original destination fails" in {
    val srcQueue : String = "myQueue"

    val router = new JmsRetryProcessor(streamsConfig, retryCfg.copy(maxRetries = 2, retryTimeout = 1.day)) {

      // This causes the send to the original destination to fail within the flow, causing
      // the envelope to travel the error path.
      override protected def sendToOriginal : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
        env.withException(new Exception("Boom"))
      }
    }

    val id : String = UUID.randomUUID().toString()

    val retryMsg : FlowEnvelope = FlowEnvelope(
      FlowMessage(FlowMessage.props(headerCfg.headerRetryDestination -> srcQueue).unwrap),
      id
    )

    withExpectedDestination(
      destName = srcQueue,
      retryProcessor = router,
      consumeAfter = consumeAfter,
      completeOn = _=> false
    )(retryMsg)(_ should be (empty))

    consumeMessages(retryCfg.failedDestName)(_.nonEmpty) match {
      case Failure(t) => fail(t)
      case Success(Nil) => fail(s"Expected message in [${retryCfg.failedDestName}]")
      case Success(env :: _) =>
        env.header[String](headerCfg.headerTransId) should be(Some(id))
        env.header[Long](headerCfg.headerRetryCount) should be(Some(3))

        consumeTransactions(_.nonEmpty) match {
          case Failure(t) => fail(t)
          case Success(Nil) => fail("Expected transaction event")
          case Success(e :: _) =>
            val t : FlowTransactionEvent = FlowTransactionEvent.envelope2event(headerCfg)(e).unwrap
            assert(t.transactionId.equals(id))
            assert(t.isInstanceOf[FlowTransactionFailed])
        }
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorFailedSpec extends ProcessorSpecSupport("JmsRetrySpec") {

  "The Jms Retry Processor should" - {

    "Deny messages that cannot be processed correctly by the retry router" in {
      val router = new JmsRetryProcessor(streamsConfig, retryCfg) {
        // This causes the send to the original destination to fail within the flow, causing
        // the envelope to travel the error path.
        override protected def sendToOriginal : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
          FlowProcessor.fromFunction("failedSendOriginal", envLogger) { env =>
            Try {
              throw new Exception("Boom")
            }
          }
        )

        // This causes the resend to the retry queue to fail, causing the envelope to be denied and causing
        // a redelivery on the retry queue (in other words the envelope will stay at the head of the queue
        override protected def sendToRetry : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
          FlowProcessor.fromFunction("failedSendRetry", envLogger) { env =>
            Try {
              throw new Exception("Boom")
            }
          }
        )
      }

      withExpectedDestination(
        destName = "myQueue",
        retryProcessor = router,
        consumeAfter = consumeAfter,
        completeOn = _ => false
      )(FlowEnvelope()){ _ should be (empty) }

      consumeMessages(retryCfg.retryDestName){_.nonEmpty}.unwrap should not be (empty)
    }
  }
}
