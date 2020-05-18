package blended.streams.jms.internal

import java.io.File
import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils._
import blended.streams.message.FlowEnvelope
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@RequiresForkedJVM
class JmsKeepAliveActorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "keepAlive").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  private implicit val timeout : FiniteDuration = 1.second
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val eCxtx : ExecutionContext = system.dispatcher

  private val ctCtxt : ContainerContext =
    mandatoryService[ContainerContext](registry)(None)

  private val cf : IdAwareConnectionFactory = {
    val p: TestProbe = TestProbe()
    system.eventStream.subscribe(p.ref, classOf[ConnectionStateChanged])
    p.fishForMessage(3.seconds){
      case ConnectionStateChanged(s) if s.status == Connected => true
      case _ => false
    }
    mandatoryService[IdAwareConnectionFactory](registry)(None)
  }

  class DummyKeepAliveProducer extends KeepAliveProducerFactory {

    val keepAliveEvents : mutable.ListBuffer[FlowEnvelope] = mutable.ListBuffer.empty

    private var prodActor : Option[ActorRef] = None

    override def start(bcf : BlendedSingleConnectionFactory): Unit = {
      val actor : ActorRef = system.actorOf(Props(new Actor() {

        private val log : Logger = Logger[DummyKeepAliveProducer]
        override def receive: Receive = {
          case env : FlowEnvelope =>
            log.info(s"Received keep alive event [$env]")
            keepAliveEvents.append(env)
        }
      }))

      prodActor = Some(actor)
      system.eventStream.publish(ProducerMaterialized(cf.vendor, cf.provider, actor))
    }

    override def stop(): Unit = prodActor.foreach(system.stop)
  }

  "The JmsKeepAliveActor should" - {

    "publish a MaxKeepAliveExceeded when the maximum number of allowed keep alives has been missed" in {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveEvent])

      val prod : DummyKeepAliveProducer = new DummyKeepAliveProducer()

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(ctCtxt, prod))

      // scalastyle:off magic.number
      ctrl ! AddedConnectionFactory(cf)
      0.to(cfg.maxKeepAliveMissed).foreach{ i =>
        probe.fishForMessage(3.seconds){
          case kam : KeepAliveMissed if kam.count == i =>
            true
        }
      }
      // scalastyle:on magic.number

      probe.fishForMessage(3.seconds){
        case _ : MaxKeepAliveExceeded => true
      }

      val envelopes : List[FlowEnvelope] = prod.keepAliveEvents.toList
      assert(envelopes.size == cfg.maxKeepAliveMissed)
      assert(envelopes.forall(e => e.header[String]("JMSCorrelationID").contains(ctCtxt.uuid)))

      ctrl ! RemovedConnectionFactory(cf)

      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
      probe.fishForMessage(3.seconds){
        case s : ConnectionStateChanged =>
          s.state.status.toString() == Connected.toString
        case _ => false
      }

      system.stop(probe.ref)
      system.stop(ctrl)
    }

    "Initiate a keep alive message / publish a KeepAliveMissed when the keep alive interval has been reached" in {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveEvent])

      val prod : DummyKeepAliveProducer = new DummyKeepAliveProducer()
      val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(ctCtxt, prod))

      // scalastyle:off magic.number
      ctrl ! AddedConnectionFactory(cf)
      Thread.sleep(cfg.keepAliveInterval.toMillis + 100)
      // scalastyle:on magic.number

      val envelopes : List[FlowEnvelope] = prod.keepAliveEvents.toList
      assert(envelopes.size == 1)
      assert(envelopes.forall(e => e.header[String]("JMSCorrelationID").contains(ctCtxt.uuid)))

      probe.fishForMessage(3.seconds){
        case _ : KeepAliveMissed => true
      }

      ctrl ! RemovedConnectionFactory(cf)
      system.stop(probe.ref)
      system.stop(ctrl)
    }

    "Reset the Keep Alive counter once a message has been received" in {
      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveMissed])
      system.eventStream.subscribe(probe.ref, classOf[MaxKeepAliveExceeded])

      val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)
      val prod : DummyKeepAliveProducer = new DummyKeepAliveProducer()

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(ctCtxt, prod))

      // scalastyle:off magic.number
      ctrl ! AddedConnectionFactory(cf)
      Thread.sleep(cfg.keepAliveInterval.toMillis + 100)
      // scalastyle:on magic.number

      probe.fishForMessage(3.seconds){
        case missed : KeepAliveMissed =>
          missed.count > 0
      }

      val envelopes : List[FlowEnvelope] = prod.keepAliveEvents.toList
      //assert(envelopes.size == 1)
      assert(envelopes.forall(e => e.header[String]("JMSCorrelationID").contains(ctCtxt.uuid)))

      system.eventStream.publish(MessageReceived(cf.vendor, cf.provider, UUID.randomUUID().toString()))

      Thread.sleep(cfg.keepAliveInterval.toMillis + 100)
      probe.fishForMessage(3.seconds){
        case missed : KeepAliveMissed =>
          missed.count == 0
      }

      ctrl ! RemovedConnectionFactory(cf)
      system.stop(probe.ref)
      system.stop(ctrl)
    }
  }
}
