package blended.akka

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill}
import akka.testkit.{TestActorRef, TestKit}
import blended.akka.protocol._
import org.scalatest._

class TestEventSource extends Actor with ActorLogging with ProductionEventSource {
  def receive = eventSourceReceive
}

class EventSourceSpec extends TestKit(ActorSystem("EventSource"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  "The EventSource" should {

    "allow to register a listener" in {
      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      real.listeners should contain(testActor)
    }

    "allow to deregister a listener" in {
      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      real.receive(DeregisterListener(testActor))
      real.listeners should have size(0)
    }

    "allow to send an event" in {
      val publisher = TestActorRef[TestEventSource]
      publisher ! RegisterListener(testActor)
      publisher ! SendEvent("TEST")
      expectMsg("TEST")
    }

    // TODO: Seems to work but is most likely timing related, may fail if the DeregisterMessage wasn't processed
    // before checking the listeners size
    "automatically deregister a listener that has died" in {

      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      testActor ! PoisonPill
      real.listeners should have size(0)
    }
  }

  override protected def afterAll() : Unit = {
    system.shutdown()
  }
}
