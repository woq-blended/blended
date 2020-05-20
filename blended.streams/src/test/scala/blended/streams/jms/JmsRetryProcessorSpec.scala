package blended.streams.jms

import java.io.File
import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionFailed}
import blended.streams.{BlendedStreamsConfig, FlowProcessor}
import blended.testsupport.pojosr.{BlendedPojoRegistry, JmsConnectionHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.RichTry._
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

abstract class ProcessorSpecSupport(name : String) extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport
  with JmsConnectionHelper {

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

  protected def streamsConfig : BlendedStreamsConfig = BlendedStreamsConfig.create(ctCtxt)

  val prefix : String = "spec"

  val brokerName : String = "retry"
  val consumerCount : Int = 5

  val amqCf : BlendedPojoRegistry => IdAwareConnectionFactory = r =>
    jmsConnectionFactory(r, mustConnect = true, timeout = timeout).get

  def producerSettings(cf : IdAwareConnectionFactory, destName : String) = JmsProducerSettings(
    log = envLogger(log),
    headerCfg = headerCfg,
    connectionFactory = cf,
    jmsDestination = Some(JmsDestination.create(destName).unwrap)
  )

  // scalastyle:off magic.number
  protected def retryCfg(cf : IdAwareConnectionFactory) : JmsRetryConfig = JmsRetryConfig(
    cf = cf,
    retryDestName = "retryQueue",
    failedDestName = "retryFailed",
    eventDestName = "internal.transactions",
    retryInterval = 500.millis,
    maxRetries = 5,
    retryTimeout = 100.millis,
    headerCfg = headerCfg
  )

  // scalastyle:on magic.number

  def withExpectedDestination(
    cf : IdAwareConnectionFactory,
    destName : String,
    retryProcessor : JmsRetryProcessor,
    consumeAfter : FiniteDuration,
    completeOn : Seq[FlowEnvelope] => Boolean,
    system : ActorSystem
  )(env : FlowEnvelope)(assertions : List[FlowEnvelope] => Unit) : Unit = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    val retryCfg : JmsRetryConfig = retryProcessor.retryCfg

    val p : Promise[Unit] = Promise()

    log.info("Starting Retry Processor ...")
    retryProcessor.start()

    sendMessages(producerSettings(cf, retryCfg.retryDestName), envLogger(log), Seq(env):_*)(system) match {
      case Success(s) =>
        akka.pattern.after(consumeAfter, system.scheduler)(Future {
          // We stop the retry processor, so that it does not process any more messages
          retryProcessor.stop()
          s.shutdown()

          log.info(s"Trying to consume messages from [$destName]")
          p.complete(consumeMessages(cf, destName, system)(completeOn).map{ env =>
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

  def consumeMessages(cf : IdAwareConnectionFactory, dest : String, system : ActorSystem)(f : Seq[FlowEnvelope] => Boolean) : Try[List[FlowEnvelope]] = Try {

    log.info(s"Consuming messages from [$dest]")
    val coll : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = cf,
      dest = JmsDestination.create(dest).get,
      log = envLogger(log),
      listener = 1,
      completeOn = Some(f),
      timeout = Some(timeout)
    )(system)

    Await.result(coll.result, timeout + 100.millis)
  }

  def consumeTransactions(cf : IdAwareConnectionFactory, system: ActorSystem, f : Seq[FlowEnvelope] => Boolean) : Try[List[FlowEnvelope]] =
    consumeMessages(cf, "internal.transactions", system)(f)

}

@RequiresForkedJVM
class JmsRetryProcessorForwardSpec extends ProcessorSpecSupport("retryForward") {

  "Consume messages from the retry destination and reinsert them into the original destination" in {

    implicit val to : FiniteDuration = timeout
    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

    val cf : IdAwareConnectionFactory = amqCf(registry)
    val rCfg : JmsRetryConfig = retryCfg(cf)
    val consumeAfter : FiniteDuration = rCfg.retryInterval * 5

    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).unwrap

    withExpectedDestination(
      cf = cf,
      destName = srcQueue,
      retryProcessor = new JmsRetryProcessor(streamsConfig, rCfg),
      consumeAfter = consumeAfter,
      completeOn = s => s.nonEmpty,
      system = system
    )(retryMsg)(
      _ should not be empty
    )
  }
}

@RequiresForkedJVM
class JmsRetryProcessorRetryCountSpec extends ProcessorSpecSupport("retryCount") {

  "Consume messages from the retry destination and pass them to the retry failed destination if the retry cont exceeds" in {

    implicit val to : FiniteDuration = timeout
    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

    val cf : IdAwareConnectionFactory = amqCf(registry)
    val rCfg : JmsRetryConfig = retryCfg(cf)
    val consumeAfter : FiniteDuration = rCfg.retryInterval * 5

    val srcQueue: String = "myQueue"

   val retryMsg: FlowEnvelope = FlowEnvelope()
     .withHeader(headerCfg.headerRetrying, "True").unwrap
     .withHeader(headerCfg.headerRetryDestination, srcQueue).unwrap
     .withHeader(headerCfg.headerRetryCount, rCfg.maxRetries).unwrap

    withExpectedDestination(
      cf = cf,
      rCfg.failedDestName,
      new JmsRetryProcessor(streamsConfig, rCfg),
      consumeAfter = consumeAfter,
      completeOn = _.nonEmpty,
      system = system
    )(retryMsg) { l =>
      l should have size 1

      l.foreach { env =>
        env.header[Long](headerCfg.headerRetryCount) should contain(rCfg.maxRetries + 1)
      }
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorRetryTimeoutSpec extends ProcessorSpecSupport("retryTimeout") {

  "Consume messages from the retry destination and pass them to the retry failed destination if the retry timeout exceeds" in {
    implicit val to : FiniteDuration = timeout
    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

    val cf : IdAwareConnectionFactory = amqCf(registry)
    val rCfg : JmsRetryConfig = retryCfg(cf)
    val consumeAfter : FiniteDuration = rCfg.retryInterval * 5

    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).unwrap
      .withHeader(headerCfg.headerFirstRetry, System.currentTimeMillis() - 2 * rCfg.retryTimeout.toMillis).unwrap

    withExpectedDestination(
      cf = cf,
      rCfg.failedDestName,
      new JmsRetryProcessor(streamsConfig, rCfg),
      consumeAfter = consumeAfter,
      completeOn = _.nonEmpty,
      system = system
    )(retryMsg) { l =>
      l should have size 1
      l.foreach { env =>
        val now : Long = System.currentTimeMillis()
        val first : Long = env.header[Long](headerCfg.headerFirstRetry).getOrElse(now)

        assert(first + rCfg.retryTimeout.toMillis <= now)
      }
    }

    val otherFailed : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = cf,
      dest = JmsQueue(rCfg.failedDestName),
      log = envLogger(log),
      listener = 1,
      completeOn = None,
      timeout = Some(timeout)
    )

    Await.result(otherFailed.result, timeout + 500.millis) should be (empty)
  }
}

@RequiresForkedJVM
class JmsRetryProcessorMissingDestinationSpec extends ProcessorSpecSupport("missingDest") {

  "Consume messages from the retry destination and pass them to the retry failed destination if no original destination is known" in {
    implicit val to : FiniteDuration = timeout
    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

    val cf : IdAwareConnectionFactory = amqCf(registry)
    val rCfg : JmsRetryConfig = retryCfg(cf)
    val consumeAfter : FiniteDuration = rCfg.retryInterval * 5

    val retryMsg : FlowEnvelope = FlowEnvelope()

    withExpectedDestination(
      cf = cf,
      rCfg.failedDestName,
      new JmsRetryProcessor(streamsConfig, rCfg),
      consumeAfter = consumeAfter,
      completeOn = _.nonEmpty,
      system = system
    )(retryMsg){ l =>
      l should have size 1
      l.foreach(_.header[Long](headerCfg.headerRetryCount) should contain (1) )
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorSendToRetrySpec extends ProcessorSpecSupport("sendToRetry") {

  "Reinsert messages into the retry destination if the send to the original destination fails" in {
    implicit val to : FiniteDuration = timeout
    implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

    val cf : IdAwareConnectionFactory = amqCf(registry)
    val rCfg : JmsRetryConfig = retryCfg(cf)
    val consumeAfter : FiniteDuration = rCfg.retryInterval * 5

    val srcQueue : String = "myQueue"

    val router = new JmsRetryProcessor(streamsConfig, rCfg.copy(maxRetries = 2, retryTimeout = 1.day)) {

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
      cf = cf,
      destName = srcQueue,
      retryProcessor = router,
      consumeAfter = consumeAfter,
      completeOn = _=> false,
      system = system
    )(retryMsg)(_ should be (empty))

    consumeMessages(cf, rCfg.failedDestName, system)(_.nonEmpty) match {
      case Failure(t) => fail(t)
      case Success(Nil) => fail(s"Expected message in [${rCfg.failedDestName}]")
      case Success(env :: _) =>
        env.header[String](headerCfg.headerTransId) should be(Some(id))
        env.header[Long](headerCfg.headerRetryCount) should be(Some(3))

        consumeTransactions(cf, system, _.nonEmpty) match {
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
      implicit val to : FiniteDuration = timeout
      implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val cf : IdAwareConnectionFactory = amqCf(registry)
      val rCfg : JmsRetryConfig = retryCfg(cf)
      val consumeAfter : FiniteDuration = rCfg.retryInterval * 5

      val router = new JmsRetryProcessor(streamsConfig, rCfg) {
        // This causes the send to the original destination to fail within the flow, causing
        // the envelope to travel the error path.
        override protected def sendToOriginal : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
          FlowProcessor.fromFunction("failedSendOriginal", envLogger(log)) { _ =>
            Try {
              throw new Exception("Boom")
            }
          }
        )

        // This causes the resend to the retry queue to fail, causing the envelope to be denied and causing
        // a redelivery on the retry queue (in other words the envelope will stay at the head of the queue
        override protected def sendToRetry : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
          FlowProcessor.fromFunction("failedSendRetry", envLogger(log)) { _ =>
            Try {
              throw new Exception("Boom")
            }
          }
        )
      }

      withExpectedDestination(
        cf = cf,
        destName = "myQueue",
        retryProcessor = router,
        consumeAfter = consumeAfter,
        completeOn = _ => false,
        system = system
      )(FlowEnvelope()){ _ should be (empty) }

      consumeMessages(cf, rCfg.retryDestName, system){_.nonEmpty}.unwrap should not be (empty)
    }
  }
}
