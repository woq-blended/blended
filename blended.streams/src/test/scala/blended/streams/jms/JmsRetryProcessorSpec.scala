package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.testkit.TestKit
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, SimpleIdAwareConnectionFactory}
import blended.streams.FlowProcessor
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.streams.transaction.FlowHeaderConfig
import blended.testsupport.RequiresForkedJVM
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

abstract class ProcessorSpecSupport(name: String) extends TestKit(ActorSystem(name))
  with LoggingFreeSpecLike
  with Matchers
  with JmsStreamSupport
  with BeforeAndAfterAll {

  lazy implicit val actorSystem : ActorSystem = system
  lazy implicit val materializer = ActorMaterializer()
  lazy implicit val eCtxt : ExecutionContext = actorSystem.dispatcher

  val log : Logger
  val prefix : String = "spec"

  val brokerName : String = "retry"
  val consumerCount : Int = 5
  val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(prefix = prefix)

  private var brokerSvc : Option[BrokerService] = None

  val amqCf : IdAwareConnectionFactory = {
    val foo = broker

    SimpleIdAwareConnectionFactory(
      vendor = "amq",
      provider = "amq",
      clientId = "spec",
      cf = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false&jms.prefetchPolicy.queuePrefetch=10")
    )
  }

  def broker : BrokerService = {
    brokerSvc match {
      case Some(s) => s
      case None =>
        val b = createBroker
        brokerSvc = Some(b)
        b
    }
  }

  private def createBroker : BrokerService = {

    val b = new BrokerService()
    b.setBrokerName(brokerName)
    b.setPersistent(false)
    b.setUseJmx(false)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)
    b.setDedicatedTaskRunner(true)

    b.start()
    b.waitUntilStarted()

    b
  }


  override protected def beforeAll(): Unit = {
    createBroker
  }

  override protected def afterAll(): Unit = {
    brokerSvc.foreach { b =>
      b.stop()
      b.waitUntilStopped()
    }
    actorSystem.terminate()
  }

  def producerSettings : String => JmsProducerSettings = destName => JmsProducerSettings(
    log = log,
    connectionFactory = amqCf,
    jmsDestination = Some(JmsDestination.create(destName).get)
  )

  def retryCfg : JmsRetryConfig = JmsRetryConfig(
    cf = amqCf,
    retryDestName = "retryQueue",
    failedDestName = "retryFailed",
    retryInterval = 2.seconds,
    maxRetries = 5,
    retryTimeout = 100.millis,
    headerCfg = headerCfg
  )

  def withExpectedDestination(
    destName : String,
    retryProcessor : JmsRetryProcessor,
    timeout : FiniteDuration = retryCfg.retryInterval + 500.millis
  )(env : FlowEnvelope): Try[List[FlowEnvelope]] = try {

    log.info("Starting Retry Processor ...")
    retryProcessor.start()

    sendMessages(producerSettings(retryCfg.retryDestName), log, Seq(env):_*) match {
      case Success(s) =>
        val msgs : List[FlowEnvelope] = consumeMessages(destName)(timeout).get
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

  def consumeMessages(dest: String)(implicit timeout : FiniteDuration) : Try[List[FlowEnvelope]] = Try {

    log.info(s"Consuming messages from [$dest]")
    val coll : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = amqCf,
      dest = JmsDestination.create(dest).get,
      log = log,
      listener = 1
    )

    Await.result(coll.result, timeout + 100.millis)
  }
}

@RequiresForkedJVM
class JmsRetryProcessorForwardSpec extends ProcessorSpecSupport("retryForward") {

  override val log: Logger = Logger[JmsRetryProcessorForwardSpec]

  "Consume messages from the retry destination and reinsert them into the original destination" in {

    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).get

    withExpectedDestination(srcQueue, new JmsRetryProcessor("spec", retryCfg))(retryMsg).get.headOption match {
      case None =>
        fail("Expected message in original JMS destination")
      case Some(env) =>
        env.header[Long](headerCfg.headerRetryCount) should be (Some(1))
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorRetryCountSpec extends ProcessorSpecSupport("retryCount") {

  override val log: Logger = Logger[JmsRetryProcessorRetryCountSpec]


  "Consume messages from the retry destination and pass them to the retry failed destination if the retry cont exceeds" in {
    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).get
      .withHeader(headerCfg.headerRetryCount, retryCfg.maxRetries).get

    withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg).get.headOption match {
      case None => fail(s"Expected message in [${retryCfg.failedDestName}]")
      case Some(env) =>
        env.header[Long](headerCfg.headerRetryCount) should be (Some(retryCfg.maxRetries + 1))
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorRetryTimeoutSpec extends ProcessorSpecSupport("retryTimeout") {

  override val log: Logger = Logger[JmsRetryProcessorRetryTimeoutSpec]

  "Consume messages from the retry destination and pass them to the retry failed destination if the retry timeout exceeds" in {
    val srcQueue : String = "myQueue"

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).get
      .withHeader(headerCfg.headerFirstRetry, System.currentTimeMillis() - 2 * retryCfg.retryTimeout.toMillis).get

    withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg).get.headOption match {
      case None => fail(s"Expected message in [${retryCfg.failedDestName}]")
      case Some(env) =>

        val now = System.currentTimeMillis()
        val first = env.header[Long](headerCfg.headerFirstRetry).get

        assert(first + retryCfg.retryTimeout.toMillis <= now)
    }
  }
}

class JmsRetryProcessorMissingDestinationSpec extends ProcessorSpecSupport("missingDest") {

  override val log: Logger = Logger[JmsRetryProcessorMissingDestinationSpec]

  "Consume messages from the retry destination and pass them to the retry failed destination if no original destination is known" in {
    val retryMsg : FlowEnvelope = FlowEnvelope()

    withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg).get.headOption match {
      case None => fail(s"Expected message in [${retryCfg.failedDestName}]")
      case Some(env) =>
        env.header[Long](headerCfg.headerRetryCount) should be (Some(1))
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorSendToRetrySpec extends ProcessorSpecSupport("sendToRetry") {

  override val log: Logger = Logger[JmsRetryProcessorSendToRetrySpec]

  "Reinsert messages into the retry destination if the send to the original destination fails" in {
    val srcQueue : String = "myQueue"

    val router = new JmsRetryProcessor("spec", retryCfg.copy(maxRetries = 2, retryTimeout = 1.day)) {

      // This causes the send to the original destination to fail within the flow, causing
      // the envelope to travel the error path.
      override protected def sendToOriginal: Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope]{ env =>
        env.withException(new Exception("Boom"))
      }
    }

    val retryMsg : FlowEnvelope = FlowEnvelope()
      .withHeader(headerCfg.headerRetryDestination, srcQueue).get

    val messages = withExpectedDestination(srcQueue, router, retryCfg.retryInterval * 3)(retryMsg).get
    messages should be (empty)

    consumeMessages(retryCfg.failedDestName)(1.second).get.headOption match {
      case None => fail(s"Expected message in [${retryCfg.failedDestName}]")
      case Some(env) =>
        env.header[Long](headerCfg.headerRetryCount) should be (Some(3))
    }
  }
}

@RequiresForkedJVM
class JmsRetryProcessorFailedSpec extends ProcessorSpecSupport("JmsRetrySpec") {

  override val log: Logger = Logger[JmsRetryProcessorFailedSpec]

  "The Jms Retry Processor should" - {

    "Deny messages that cannot be processed correctly by the retry router" in {
      val router = new JmsRetryProcessor("spec", retryCfg) {

        // This causes the send to the original destination to fail within the flow, causing
        // the envelope to travel the error path.
        override protected def sendToOriginal : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
          FlowProcessor.fromFunction("failedSendOriginal", log) { env =>
            Try {
              throw new Exception("Boom")
            }
          }
        )

        // This causes the resend to the retry queue to fail, causing the envelope to be denied and causing
        // a redelivery on the retry queue (in other words the envelope will stay at the head of the queue
        override protected def sendToRetry : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
          FlowProcessor.fromFunction("failedSendRetry", log) { env =>
            Try {
              throw new Exception("Boom")
            }
          }
        )
      }

      val messages = withExpectedDestination("myQueue", router)(FlowEnvelope()).get
      messages should be (empty)
      consumeMessages(retryCfg.retryDestName)(1.second).get should not be (empty)
    }
  }
}
