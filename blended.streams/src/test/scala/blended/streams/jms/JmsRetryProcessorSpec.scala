package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, Materializer}
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

@RequiresForkedJVM
class JmsRetryProcessorSpec extends TestKit(ActorSystem("JmsRetrySpec"))
  with LoggingFreeSpecLike
  with Matchers
  with JmsStreamSupport
  with BeforeAndAfterAll {

  private val brokerName : String = "retry"
  private val consumerCount : Int = 5
  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(prefix = "spec")

  private lazy val amqCf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
    vendor = "amq",
    provider = "amq",
    clientId = "spec",
    cf = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false&jms.prefetchPolicy.queuePrefetch=10")
  )

  private val broker : BrokerService = {

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

  private implicit val actorSystem : ActorSystem = system
  private implicit val materializer : Materializer = ActorMaterializer()
  private implicit val eCtxt : ExecutionContext = system.dispatcher

  private val log : Logger = Logger[JmsRetryProcessorSpec]

  override protected def afterAll(): Unit = {
    broker.stop()
    broker.waitUntilStopped()
    system.terminate()
  }

  private val producerSettings : String => JmsProducerSettings = destName => JmsProducerSettings(
    log = log,
    connectionFactory = amqCf,
    jmsDestination = Some(JmsDestination.create(destName).get)
  )

  private val retryCfg : JmsRetryConfig = JmsRetryConfig(
    cf = amqCf,
    retryDestName = "retryQueue",
    failedDestName = "retryFailed",
    retryInterval = 2.seconds,
    maxRetries = 5,
    retryTimeout = 100.millis,
    headerCfg = headerCfg
  )

  def withExpectedDestination(destName : String, retryProcessor : JmsRetryProcessor)(env : FlowEnvelope): Try[List[FlowEnvelope]] = try {

    log.info("Starting Retry Processor ...")
    retryProcessor.start()

    sendMessages(producerSettings(retryCfg.retryDestName), log, Seq(env):_*) match {
      case Success(s) =>
        val msgs : List[FlowEnvelope] = consumeMessages(destName)(retryCfg.retryInterval).get
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

  "The Jms Retry Processor should" - {

//    "Consume messages from the retry destination and reinsert them into the original destination" in {
//
//      val srcQueue : String = "myQueue"
//
//      val retryMsg : FlowEnvelope = FlowEnvelope()
//        .withHeader(headerCfg.headerRetryDestination, srcQueue).get
//
//      val env = withExpectedDestination(srcQueue, new JmsRetryProcessor("spec", retryCfg))(retryMsg).get
//
//      env.header[Long](headerCfg.headerRetryCount) should be (Some(1))
//    }
//
//    "Consume messages from the retry destination and pass them to the retry failed destination if the retry cont exceeds" in {
//      val srcQueue : String = "myQueue"
//
//      val retryMsg : FlowEnvelope = FlowEnvelope()
//        .withHeader(headerCfg.headerRetryDestination, srcQueue).get
//        .withHeader(headerCfg.headerRetryCount, retryCfg.maxRetries).get
//
//      withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg)
//    }
//
//    "Consume messages from the retry destination and pass them to the retry failed destination if the retry timeout exceeds" in {
//      val srcQueue : String = "myQueue"
//
//      val retryMsg : FlowEnvelope = FlowEnvelope()
//        .withHeader(headerCfg.headerRetryDestination, srcQueue).get
//        .withHeader(headerCfg.headerFirstRetry, System.currentTimeMillis() - 2 * retryCfg.retryTimeout.toMillis).get
//
//      withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg)
//    }
//
//    "Consume messages from the retry destination and pass them to the retry failed destination if no original destination is known" in {
//      val retryMsg : FlowEnvelope = FlowEnvelope()
//
//      withExpectedDestination(retryCfg.failedDestName, new JmsRetryProcessor("spec", retryCfg))(retryMsg)
//    }

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

    "Retry messages that cannot be sent onwards to the retry destination" in {
      pending
    }

  }
}
