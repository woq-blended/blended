package blended.streams.jms.internal

import java.io.File
import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils._
import blended.streams.message.FlowEnvelope
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

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

  private val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)

  class DummyKeepAliveProducer extends KeepAliveProducerFactory {

    val keepAliveEvents : mutable.ListBuffer[FlowEnvelope] = mutable.ListBuffer.empty

    override val createProducer: BlendedSingleConnectionFactory => Future[ActorRef] = { _ => Future {
      system.actorOf(Props(new Actor() {
        override def receive: Receive = {
          case env : FlowEnvelope => keepAliveEvents.append(env)
        }
      }))
    }}
  }

  "The JmsKeepAliveActor should" - {

    "publish a MaxKeepAliveExceeded when the maximum number of allowed keep alives has been missed" in {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveEvent])

      val prod : DummyKeepAliveProducer = new DummyKeepAliveProducer()
      val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(idSvc, prod))

      // scalastyle:off magic.number
      ctrl ! AddedConnectionFactory(cf)
      0.until(cfg.maxKeepAliveMissed).foreach{ _ =>
        probe.expectMsgClass(classOf[KeepAliveMissed])
      }
      // scalastyle:on magic.number

      val envelopes : List[FlowEnvelope] = prod.keepAliveEvents.toList
      assert(envelopes.size == cfg.maxKeepAliveMissed)
      assert(envelopes.forall(e => e.header[String]("JMSCorrelationID").contains(idSvc.uuid)))

      probe.fishForMessage(3.seconds){
        case _ : MaxKeepAliveExceeded => true
      }

      ctrl ! RemovedConnectionFactory(cf)
      system.stop(ctrl)
    }

    "Initiate a keep alive message / publish a KeepAliveMissed when the keep alive interval has been reached" in {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveEvent])

      val prod : DummyKeepAliveProducer = new DummyKeepAliveProducer()
      val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(idSvc, prod))

      // scalastyle:off magic.number
      ctrl ! AddedConnectionFactory(cf)
      Thread.sleep(cfg.keepAliveInterval.toMillis + 100)
      // scalastyle:on magic.number

      val envelopes : List[FlowEnvelope] = prod.keepAliveEvents.toList
      assert(envelopes.size == 1)
      assert(envelopes.forall(e => e.header[String]("JMSCorrelationID").contains(idSvc.uuid)))

      probe.fishForMessage(3.seconds){
        case _ : KeepAliveMissed => true
      }

      ctrl ! RemovedConnectionFactory(cf)
      system.stop(ctrl)
    }

    "Reset the Keep Alive counter once a message has been received" in {
      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[KeepAliveMissed])
      system.eventStream.subscribe(probe.ref, classOf[MaxKeepAliveExceeded])

      val prod : DummyKeepAliveProducer = new DummyKeepAliveProducer()
      val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)

      val cfg : ConnectionConfig = cf.asInstanceOf[BlendedSingleConnectionFactory].config
      val ctrl : ActorRef = system.actorOf(JmsKeepAliveController.props(idSvc, prod))

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
      assert(envelopes.forall(e => e.header[String]("JMSCorrelationID").contains(idSvc.uuid)))

      system.eventStream.publish(MessageReceived(cf.vendor, cf.provider, UUID.randomUUID().toString()))

      Thread.sleep(cfg.keepAliveInterval.toMillis + 100)
      probe.fishForMessage(3.seconds){
        case missed : KeepAliveMissed =>
          missed.count == 0
      }

      ctrl ! RemovedConnectionFactory(cf)
      system.stop(ctrl)
    }
  }
}
