package blended.jms.bridge.internal

import java.io.File
import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.FlowProcessor
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.{FlowHeaderConfig, FlowTransactionEvent, FlowTransactionFailed}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import blended.util.RichTry._

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

  val prefix : String = "spec"

  val brokerName : String = "retry"
  val consumerCount : Int = 5
  val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(prefix = prefix)

  val amqCf : IdAwareConnectionFactory =
    mandatoryService[IdAwareConnectionFactory](registry)(Some("(&(vendor=activemq)(provider=activemq))"))

  def producerSettings : String => JmsProducerSettings = destName => JmsProducerSettings(
    log = log,
    headerCfg = headerCfg,
    connectionFactory = amqCf,
    jmsDestination = Some(JmsDestination.create(destName).unwrap)
  )

  // scalastyle:off magic.number
  def retryCfg : JmsRetryConfig = JmsRetryConfig(
    cf = amqCf,
    retryDestName = "retryQueue",
    failedDestName = "retryFailed",
    eventDestName = "internal.transactions",
    retryInterval = 1.seconds,
    maxRetries = 5,
    retryTimeout = 100.millis,
    headerCfg = headerCfg
  )
  // scalastyle:on magic.number

  def withExpectedDestination(
    destName : String,
    retryProcessor : JmsRetryProcessor,
    timeout : FiniteDuration = retryCfg.retryInterval + 500.millis
  )(env : FlowEnvelope) : Try[List[FlowEnvelope]] = try {

    log.info("Starting Retry Processor ...")
    retryProcessor.start()

    sendMessages(producerSettings(retryCfg.retryDestName), log, Seq(env) : _*) match {
      case Success(s) =>
        val msgs : List[FlowEnvelope] = consumeMessages(destName)(timeout).unwrap
        s.shutdown()
        Success(msgs)

      case Failure(exception) => Failure(exception)
    }
  } catch {
    case NonFatal(t) => Failure(t)
  } finally {
    log.info("Stopping Retry Processor ...")
    retryProcessor.stop()
  }

  def consumeMessages(dest : String)(implicit timeout : FiniteDuration) : Try[List[FlowEnvelope]] = Try {

    log.info(s"Consuming messages from [$dest]")
    val coll : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = amqCf,
      dest = JmsDestination.create(dest).unwrap,
      log = log,
      listener = 1
    )

    Await.result(coll.result, timeout + 100.millis)
  }

  def consumeTransactions()(implicit timeout : FiniteDuration) : Try[List[FlowEnvelope]] =
    consumeMessages("internal.transactions")

}

@RequiresForkedJVM
class JmsRetryProcessorForwardSpec extends ProcessorSpecSupport("retryForward") {

  "Consume messages from the retry destination and reinsert them into the original destination" in {

    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).unwrap

    withExpectedDestination(srcQueue, new JmsRetryProcessor("spec", retryCfg))(retryMsg).unwrap.headOption match {
      case None =>
        fail("Expected message in original JMS destination")
      case Some(env) =>
        env.header[Long](headerCfg.headerRetryCount) should be(Some(1))
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorRetryCountSpec extends ProcessorSpecSupport("retryCount") {

  "Consume messages from the retry destination and pass them to the retry failed destination if the retry cont exceeds" in {
    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).unwrap
      .withHeader(headerCfg.headerRetryCount, retryCfg.maxRetries).unwrap

    withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg).unwrap.headOption match {
      case None => fail(s"Expected message in [${retryCfg.failedDestName}]")
      case Some(env) =>
        env.header[Long](headerCfg.headerRetryCount) should be(Some(retryCfg.maxRetries + 1))
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

    withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg).unwrap.headOption match {
      case None => fail(s"Expected message in [${retryCfg.failedDestName}]")
      case Some(env) =>

        val now : Long = System.currentTimeMillis()
        val first : Long = env.header[Long](headerCfg.headerFirstRetry).getOrElse(now)

        assert(first + retryCfg.retryTimeout.toMillis <= now)
    }
  }
}

class JmsRetryProcessorMissingDestinationSpec extends ProcessorSpecSupport("missingDest") {

  "Consume messages from the retry destination and pass them to the retry failed destination if no original destination is known" in {
    val retryMsg : FlowEnvelope = FlowEnvelope()

    withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg).unwrap.headOption match {
      case None =>
        fail(s"Expected message in [${retryCfg.failedDestName}]")

      case Some(env) =>
        env.header[Long](headerCfg.headerRetryCount) should be(Some(1))
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorSendToRetrySpec extends ProcessorSpecSupport("sendToRetry") {

  "Reinsert messages into the retry destination if the send to the original destination fails" in {
    val srcQueue : String = "myQueue"

    val router = new JmsRetryProcessor("spec", retryCfg.copy(maxRetries = 2, retryTimeout = 1.day)) {

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

    val messages = withExpectedDestination(srcQueue, router, retryCfg.retryInterval * 5)(retryMsg).unwrap
    messages should be(empty)

    consumeMessages(retryCfg.failedDestName)(1.second).unwrap.headOption match {
      case None => fail(s"Expected message in [${retryCfg.failedDestName}]")
      case Some(env) =>
        // Make sure the message has travelled [maxRetries] loops
        env.header[String](headerCfg.headerTransId) should be(Some(id))
        env.header[Long](headerCfg.headerRetryCount) should be(Some(3))

        val events = consumeTransactions().unwrap
        events should have size 1

        // We lso expect a failed transaction event in the transactions destinations
        events.headOption match {
          case None =>
            fail("Expected transaction failed event")
          case Some(e) =>
            e.header[String](headerCfg.headerTransId) should be(Some(id))

            val t = FlowTransactionEvent.envelope2event(headerCfg)(e).unwrap
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
      val router = new JmsRetryProcessor("spec", retryCfg) {

        // This causes the send to the original destination to fail within the flow, causing
        // the envelope to travel the error path.
        override protected def sendToOriginal : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
          FlowProcessor.fromFunction("failedSendOriginal", log) { _ =>
            Try {
              throw new Exception("Boom")
            }
          }
        )

        // This causes the resend to the retry queue to fail, causing the envelope to be denied and causing
        // a redelivery on the retry queue (in other words the envelope will stay at the head of the queue
        override protected def sendToRetry : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
          FlowProcessor.fromFunction("failedSendRetry", log) { _ =>
            Try {
              throw new Exception("Boom")
            }
          }
        )
      }

      val messages = withExpectedDestination("myQueue", router)(FlowEnvelope()).unwrap
      messages should be(empty)
      consumeMessages(retryCfg.retryDestName)(1.second).unwrap should not be (empty)
    }
  }
}
