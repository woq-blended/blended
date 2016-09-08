package blended.akka

import akka.actor._
import akka.event.LoggingReceive
import akka.testkit.{TestActorRef, TestLatch, TestProbe}
import blended.akka.protocol._
import blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await

object OSGIActorDummyPublisher {
  def apply(actorConfig: OSGIActorConfig) = new OSGIActorDummyPublisher(actorConfig)
}

class OSGIActorDummyPublisher(actorConfig: OSGIActorConfig) extends OSGIActor(actorConfig) with ProductionEventSource {

  def receive = LoggingReceive { eventSourceReceive }
}

//----------

object OSGIDummyListener {
  def apply(cfg : OSGIActorConfig) = new OSGIDummyListener(cfg) with OSGIEventSourceListener
}

class OSGIDummyListener(cfg: OSGIActorConfig) extends OSGIActor(cfg) { this : OSGIEventSourceListener =>

  implicit val actorSys = context.system
  val latch = TestLatch(1)
  
  val publisherName = bundleActorConfig.getString("listener.publisher")
  
  def receive =  testing orElse eventListenerReceive(publisherName)

  def testing : Receive = {
    case "Andreas" => latch.countDown()
  }
}

//----------

class OSGIEventSourceListenerSpec extends WordSpec
  with Matchers
  with TestSetup
  with MockitoSugar {

  "The OSGI Event source listener" should {

    "subscribe to a publisher bundle if it already exists in the actor system" in TestActorSys { testkit =>

      implicit val system = testkit.system

      import scala.concurrent.duration._

      val probe = TestProbe()

      system.eventStream.subscribe(probe.ref, classOf[BundleActorStarted])

      val publisher = TestActorRef(Props(OSGIActorDummyPublisher(testActorConfig("publisher", system))), "publisher")
      val listener = TestActorRef(Props(OSGIDummyListener(testActorConfig("listener", system))), "listener")

      system.eventStream.publish(BundleActorStarted("publisher"))
      system.eventStream.publish(BundleActorStarted("listener"))

      // We need to wait for the Actor bundle to finish it's initialization
      probe.fishForMessage() {
        case BundleActorStarted(s) if s == "listener" => true
        case _ => false
      }

      //TODO: This is a test only hack to give the listener some time to finish its registration
      Thread.sleep(100)

      publisher ! SendEvent("Andreas")

      val latch = listener.underlyingActor.asInstanceOf[OSGIDummyListener].latch
      Await.ready(latch, 1.second)
      latch.isOpen should be (true)
    }
  }

  "start referring to the dlc when the publisher is unavailbale" in TestActorSys { testkit =>

    implicit val system = testkit.system

    val listener = TestActorRef(Props(OSGIDummyListener(testActorConfig("listener", system))), "listener")

    val probe = TestProbe()

    system.eventStream.subscribe(probe.ref, classOf[BundleActorStarted])
    system.eventStream.publish(BundleActorStarted("listener"))

    // We need to wait for the Actor bundle to finish it's initialization
    probe.fishForMessage() {
      case BundleActorStarted(s) if s == "listener" => true
      case _ => false
    }

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(system.deadLetters)
  }

  "subscribe to the publisher when it becomes available" in TestActorSys { testkit =>

    import scala.concurrent.duration._

    implicit val system = testkit.system

    val probe = TestProbe()

    system.eventStream.subscribe(probe.ref, classOf[BundleActorStarted])

    val listener = TestActorRef(Props(OSGIDummyListener(testActorConfig("listener", system))), "listener")
    system.eventStream.publish(BundleActorStarted("listener"))

    // We need to wait for the Actor bundle to finish it's initialization
    probe.fishForMessage() {
      case BundleActorStarted(s) if s == "listener" => true
      case _ => false
    }

    val publisher = TestActorRef(Props(OSGIActorDummyPublisher(testActorConfig("publisher", system))), "publisher")
    system.eventStream.publish(BundleActorStarted("publisher"))

    //TODO: This is a test only hack to give the listener some time to finish its registration
    Thread.sleep(100)
    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(publisher)

    publisher ! SendEvent("Andreas")
    val latch = listener.underlyingActor.asInstanceOf[OSGIDummyListener].latch
    Await.ready(latch, 1.second)
    latch.isOpen should be (true)
  }

  "fallback to system.dlc when the publisher becomes unavailable" in TestActorSys { testkit =>

    implicit val system = testkit.system

    val listener = TestActorRef(Props(OSGIDummyListener(testActorConfig("listener", system))), "listener")

    val probe = TestProbe()

    system.eventStream.subscribe(probe.ref, classOf[BundleActorStarted])
    system.eventStream.publish(BundleActorStarted("listener"))

    // We need to wait for the Actor bundle to finish it's initialization
    probe.fishForMessage() {
      case BundleActorStarted(s) if s == "listener" => true
      case _ => false
    }

    val publisher = TestActorRef(Props(OSGIActorDummyPublisher(testActorConfig("publisher", system))), "publisher")
    system.eventStream.publish(BundleActorStarted("publisher"))

    val watcher = new TestProbe(system)
    watcher.watch(publisher)

    val listenerReal = listener.underlyingActor.asInstanceOf[OSGIEventSourceListener]
    listenerReal.publisher should be(publisher)

    system.stop(publisher)
    watcher.expectMsgType[Terminated]

    listenerReal.publisher should be (system.deadLetters)
  }
}
