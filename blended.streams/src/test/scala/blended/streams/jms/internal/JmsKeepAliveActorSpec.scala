package blended.streams.jms.internal

import java.io.File
import java.util.UUID
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils._
import blended.streams.BlendedStreamsConfig
import blended.streams.message.FlowEnvelope
import blended.testsupport.pojosr.{JmsConnectionHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

@RequiresForkedJVM
class JmsKeepAliveActorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsConnectionHelper {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "keepAlive").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  class DummyKeepAliveProducer(
    override val ctCtxt : ContainerContext,
    override val cf : BlendedSingleConnectionFactory,
    system : ActorSystem
  ) extends KeepAliveProducerFactory {

    val keepAliveEvents : mutable.ListBuffer[FlowEnvelope] = mutable.ListBuffer.empty

    private var prodActor : Option[ActorRef] = None

    override def start(): Unit = {
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

    val pFactory = (system : ActorSystem) => (ctContext : ContainerContext, cf : BlendedSingleConnectionFactory, sCfg: BlendedStreamsConfig) =>
      new DummyKeepAliveProducer(ctContext, cf, system)

    "publish a MaxKeepAliveExceeded when the maximum number of allowed keep alives has been reached" in {
      val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val cf : BlendedSingleConnectionFactory =
        jmsConnectionFactory(registry, mustConnect = true, timeout = timeout).get.asInstanceOf[BlendedSingleConnectionFactory]

      val probe : TestProbe = TestProbe()(system)
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveEvent])

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(ctCtxt, BlendedStreamsConfig.create(ctCtxt), pFactory(system)))

      // scalastyle:off magic.number
      ctrl ! AddedConnectionFactory(cf)
      0.to(cfg.maxKeepAliveMissed).foreach{ i =>
        probe.fishForMessage(timeout){
          case kam : KeepAliveMissed =>
            kam.count == i && kam.corrId == s"${ctCtxt.uuid}-${cf.vendor}-${cf.provider}"
        }
      }
      // scalastyle:on magic.number

      probe.fishForMessage(timeout){
        case _ : MaxKeepAliveExceeded => true
      }

      ctrl ! RemovedConnectionFactory(cf)

      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
      probe.fishForMessage(timeout){
        case s : ConnectionStateChanged =>
          s.state.status.toString() == Connected.toString
        case _ => false
      }

      system.stop(probe.ref)
      system.stop(ctrl)
    }

    "Initiate a keep alive message / publish a KeepAliveMissed when the keep alive interval has been reached" in {
      val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val cf : BlendedSingleConnectionFactory =
        jmsConnectionFactory(registry, mustConnect = true, timeout = timeout).get.asInstanceOf[BlendedSingleConnectionFactory]

      val probe : TestProbe = TestProbe()(system)
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveEvent])

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(ctCtxt, BlendedStreamsConfig.create(ctCtxt), pFactory(system)))

      // scalastyle:off magic.number
      ctrl ! AddedConnectionFactory(cf)
      Thread.sleep(cfg.keepAliveInterval.toMillis + 100)
      // scalastyle:on magic.number

      probe.fishForMessage(timeout){
        case _ : KeepAliveMissed => true
      }

      ctrl ! RemovedConnectionFactory(cf)
      system.stop(probe.ref)
      system.stop(ctrl)
    }

    "Reset the Keep Alive counter once a message has been received" in {
      val system : ActorSystem = mandatoryService[ActorSystem](registry)

      val cf : BlendedSingleConnectionFactory =
        jmsConnectionFactory(registry, mustConnect = true, timeout = timeout).get.asInstanceOf[BlendedSingleConnectionFactory]

      val probe : TestProbe = TestProbe()(system)
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveMissed])
      system.eventStream.subscribe(probe.ref, classOf[MaxKeepAliveExceeded])

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(ctCtxt, BlendedStreamsConfig.create(ctCtxt), pFactory(system)))

      // scalastyle:off magic.number
      ctrl ! AddedConnectionFactory(cf)
      Thread.sleep(cfg.keepAliveInterval.toMillis + 100)
      // scalastyle:on magic.number

      probe.fishForMessage(timeout){
        case missed : KeepAliveMissed =>
          missed.count > 0
      }

      system.eventStream.publish(MessageReceived(cf.vendor, cf.provider, UUID.randomUUID().toString()))

      probe.fishForMessage(timeout){
        case missed : KeepAliveMissed =>
          missed.count == 0
      }

      ctrl ! RemovedConnectionFactory(cf)
      system.stop(probe.ref)
      system.stop(ctrl)
    }
  }
}
