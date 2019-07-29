package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
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

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}
import blended.util.RichTry._

@RequiresForkedJVM
class JmsAckSourceSpec extends TestKit(ActorSystem("JmsAckSource"))
  with LoggingFreeSpecLike
  with Matchers
  with JmsStreamSupport
  with BeforeAndAfterAll {

  private val brokerName : String = "blended"
  private val consumerCount : Int = 5
  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(prefix = "Spec")

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

  private val log : Logger = Logger[JmsAckSourceSpec]

  val envelopes : Int => Seq[FlowEnvelope] = msgCount => 1.to(msgCount).map { i =>
    FlowEnvelope().withHeader("msgNo", i).unwrap
  }

  override protected def afterAll() : Unit = {
    broker.stop()
    broker.waitUntilStopped()
    system.terminate()
  }

  private val consumerSettings : String => JMSConsumerSettings = destName => {

    val dest = JmsDestination.create(destName).unwrap

    JMSConsumerSettings(
      log = log,
      headerCfg = headerCfg,
      connectionFactory = amqCf,
      jmsDestination = Some(dest),
      sessionCount = consumerCount,
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge
    )
  }

  private val producerSettings : String => JmsProducerSettings = destName => JmsProducerSettings(
    log = log,
    headerCfg = headerCfg,
    connectionFactory = amqCf,
    jmsDestination = Some(JmsDestination.create(destName).get)
  )

  private val consumer : JMSConsumerSettings => Option[FiniteDuration] => Source[FlowEnvelope, NotUsed] =
    cSettings => minMessageDelay => jmsConsumer(
      name = "test",
      settings = cSettings,
      minMessageDelay = minMessageDelay
    )

  "The JMS Ack Source should" - {

    "consume and acknowledge messages without delay correctly" in {

      val msgCount : Int = 100
      val destName : String = "noDelay"

      val cSettings : JMSConsumerSettings = consumerSettings(destName)
      val pSettings : JmsProducerSettings = producerSettings(destName)

      val msgConsumer : Source[FlowEnvelope, NotUsed] = consumer(cSettings)(None)

      sendMessages(
        pSettings,
        log,
        envelopes(msgCount) : _*
      ) match {
          case Success(s) =>
            val coll : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit(
              name = "ackConsumer",
              source = msgConsumer,
              timeout = 5.seconds
            )(e => e.acknowledge())

            val result : List[Int] = Await.result(coll.result, 6.seconds).map { env => env.header[Int]("msgNo").get }
            val missing : List[Int] = 1.to(msgCount).filter(i => !result.contains(i)).toList
            missing should be(empty)

            s.shutdown()
          case Failure(t) =>
            fail(t)
        }
    }

    "not consume messages before the minimum message delay is reached" in {

      val msgCount : Int = 10
      val destName : String = "delayed"
      val minDelay : FiniteDuration = 3.seconds

      val cSettings : JMSConsumerSettings = consumerSettings(destName)
      val pSettings : JmsProducerSettings = producerSettings(destName)

      val msgConsumer : Source[FlowEnvelope, NotUsed] = consumer(cSettings)(Some(minDelay))

      sendMessages(
        pSettings,
        log,
        envelopes(msgCount) : _*
      ) match {
          case Success(s) =>
            val coll : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit(
              name = "delayedConsumer",
              source = msgConsumer,
              timeout = minDelay - 1.seconds
            )(e => e.acknowledge())

            val result : List[Int] = Await.result(coll.result, minDelay + 1.second).map { env => env.header[Int]("msgNo").get }
            result should be(empty)

            s.shutdown()

            val coll2 : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit(
              name = "delayedConsumer2",
              source = msgConsumer,
              timeout = minDelay + 500.millis
            )(e => e.acknowledge())

            val result2 : List[Int] = Await.result(coll2.result, minDelay + 1.seconds).map { env => env.header[Int]("msgNo").get }
            result2 should have size msgCount

          case Failure(t) => fail(t)
        }
    }
  }
}
