package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, SimpleIdAwareConnectionFactory}
import blended.streams.StreamFactories
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

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

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

  private val consumerSettings : String => JMSConsumerSettings = destName => {

    val dest = JmsDestination.create(destName).get

    JMSConsumerSettings(
      log = log,
      connectionFactory = amqCf,
      jmsDestination = Some(dest),
      sessionCount = consumerCount,
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge
    )
  }

  private val producerSettings : String => JmsProducerSettings = destName => JmsProducerSettings(
    log = log,
    connectionFactory = amqCf,
    jmsDestination = Some(JmsDestination.create(destName).get)
  )

  private val jmsConsumer : JMSConsumerSettings => Option[FiniteDuration] => Source[FlowEnvelope, NotUsed] =
    cSettings => minMessageDelay => restartableConsumer(
      name = "retry",
      settings = cSettings,
      headerConfig = headerCfg,
      minMessageDelay = minMessageDelay
    ).via(Flow.fromFunction{env =>
      env.acknowledge()
      env
    })

  private val retryCfg : JmsRetryConfig = JmsRetryConfig(
    cf = amqCf,
    retryDestName = "retryQueue",
    failedDestName = "retryFailed",
    retryInterval = 3.seconds,
    maxRetries = 5,
    retryTimeout = 100.millis,
    headerCfg = headerCfg
  )

  def withExpectedDestination(destName : String)(env : FlowEnvelope): Unit ={

    val retryProcessor = JmsRetryProcessor("spec", retryCfg)
    retryProcessor.start()

    sendMessages(producerSettings(retryCfg.retryDestName), log, Seq(env):_*) match {
      case Success(s) =>
        val coll : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit(
          name = "retryConsumer",
          source = jmsConsumer(consumerSettings(destName))(None),
          timeout = retryCfg.retryInterval + 500.millis
        )(e => e.acknowledge())

        s.shutdown()

        val result : List[FlowEnvelope] = Await.result(coll.result, retryCfg.retryInterval + 1.second)
        result should have size (1)

      case Failure(t) => fail(t)
    }

  }

  "The Jms Retry Processor should" - {

    "Consume messages from the retry destination and reinsert them into the original destination" in {

      val srcQueue : String = "myQueue"

      val retryMsg : FlowEnvelope = FlowEnvelope()
        .withHeader(headerCfg.headerRetryDestination, srcQueue).get

      withExpectedDestination(srcQueue)(retryMsg)
    }

    "Consume messages from the retry destination and pass them to the retry failed destination if the retry cont exceeds" in {
      val srcQueue : String = "myQueue"

      val retryMsg : FlowEnvelope = FlowEnvelope()
        .withHeader(headerCfg.headerRetryDestination, srcQueue).get
        .withHeader(headerCfg.headerRetryCount, retryCfg.maxRetries).get

      withExpectedDestination(retryCfg.failedDestName)(retryMsg)
    }

    "Consume messages from the retry destination and pass them to the retry failed destination if the retry timeout exceeds" in {
      val srcQueue : String = "myQueue"

      val retryMsg : FlowEnvelope = FlowEnvelope()
        .withHeader(headerCfg.headerRetryDestination, srcQueue).get
        .withHeader(headerCfg.headerFirstRetry, System.currentTimeMillis() - 2 * retryCfg.retryTimeout.toMillis).get

      withExpectedDestination(retryCfg.failedDestName)(retryMsg)
    }

    "Consume messages from the retry destination and pass them to the retry failed destination if no original destination is known" in {
      val retryMsg : FlowEnvelope = FlowEnvelope()

      withExpectedDestination(retryCfg.failedDestName)(retryMsg)
    }

  }
}
