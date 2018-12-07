package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, SimpleIdAwareConnectionFactory}
import blended.streams.{StreamController, StreamControllerConfig, StreamFactories}
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

@RequiresForkedJVM
class JmsAckSourceSpec extends TestKit(ActorSystem("JmsAckSource"))
  with LoggingFreeSpecLike
  with Matchers
  with JmsStreamSupport
  with BeforeAndAfterAll {

  lazy val brokerName : String = "blended"

  private def amqCf(): IdAwareConnectionFactory = new SimpleIdAwareConnectionFactory(
    vendor = "amq",
    provider = "amq",
    clientId = "spec",
    cf = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false")
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

  implicit val actorSystem : ActorSystem = system
  implicit val materializer : Materializer = ActorMaterializer()
  implicit val eCtxt : ExecutionContext = system.dispatcher

  val log : Logger = Logger[JmsAckSourceSpec]

  val msgCount : Int = 100

  override protected def afterAll(): Unit = {
    broker.stop()
    broker.waitUntilStopped()
    system.terminate()
  }

  "The JMS Ack Source should" - {

    "Consume and acknowledge messages correctly" in {

      val dest = JmsDestination.create("testQueue").get
      val cf = amqCf()

      val settings : JMSConsumerSettings = JMSConsumerSettings(
        connectionFactory = cf,
        jmsDestination = Some(dest),
        sessionCount = 5,
        acknowledgeMode = AcknowledgeMode.ClientAcknowledge
      )

      val consumer : Source[FlowEnvelope, NotUsed] = restartableConsumer(
        name = "test",
        settings = settings,
        headerConfig = FlowHeaderConfig(prefix = "Spec"),
        log =log
      ).via(Flow.fromFunction{env =>
        env.acknowledge()
        env
      })

      val envelopes : Seq[FlowEnvelope] = 1.to(msgCount).map { i =>
        FlowEnvelope().withHeader("msgNo", i).get
      }

      val switch = sendMessages(cf, dest, log, envelopes:_*)

      val coll : Collector[FlowEnvelope] = StreamFactories.runSourceWithTimeLimit(
        name = "ackConsumer",
        source = consumer,
        timeout = 5.seconds
      )(e => e.acknowledge())

      val result = Await.result(coll.result, 11.seconds).map{ env => env.header[Int]("msgNo").get }
      result should have size (envelopes.size)

      switch.shutdown()
    }
  }

}
