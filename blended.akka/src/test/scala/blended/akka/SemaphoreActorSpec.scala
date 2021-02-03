package blended.akka

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.matchers.should.Matchers

class SemaphoreActorSpec extends TestKit(ActorSystem("semaphore"))
  with LoggingFreeSpecLike
  with Matchers
  with ImplicitSender {

  "The Semaphore Actor should" - {

    "Allow to acquire a lock" in {

      val probe : TestProbe = TestProbe()
      val sem : ActorRef = system.actorOf(Props[SemaphoreActor]())

      sem ! SemaphoreActor.Acquire(probe.ref)

      probe.expectMsg(SemaphoreActor.Acquired)
    }

    "Allow to acquire the lock if the lock is already acquired by the same actor" in {
      val probe : TestProbe = TestProbe()
      val sem : ActorRef = system.actorOf(Props[SemaphoreActor]())

      sem ! SemaphoreActor.Acquire(probe.ref)
      probe.expectMsg(SemaphoreActor.Acquired)

      sem ! SemaphoreActor.Acquire(probe.ref)
      probe.expectMsg(SemaphoreActor.Acquired)
    }

    "Deny to acquire a lock as long as another Actor is holding the semaphore" in {
      val probe1 : TestProbe = TestProbe()
      val probe2 : TestProbe = TestProbe()

      val sem : ActorRef = system.actorOf(Props[SemaphoreActor]())

      sem ! SemaphoreActor.Acquire(probe1.ref)
      probe1.expectMsg(SemaphoreActor.Acquired)

      sem ! SemaphoreActor.Acquire(probe2.ref)
      probe2.expectMsg(SemaphoreActor.Waiting)
    }

    "Allow to acquire a lock after the semaphore has been released" in {
      val probe1 : TestProbe = TestProbe()
      val probe2 : TestProbe = TestProbe()

      val sem : ActorRef = system.actorOf(Props[SemaphoreActor]())

      sem ! SemaphoreActor.Acquire(probe1.ref)
      probe1.expectMsg(SemaphoreActor.Acquired)

      sem ! SemaphoreActor.Acquire(probe2.ref)
      probe2.expectMsg(SemaphoreActor.Waiting)

      sem ! SemaphoreActor.Release(probe1.ref)
      probe2.expectMsg(SemaphoreActor.Acquired)
    }

    "Release the lock if the locking actor dies" in {
      val probe1 : TestProbe = TestProbe()
      val probe2 : TestProbe = TestProbe()

      val sem : ActorRef = system.actorOf(Props[SemaphoreActor]())

      sem ! SemaphoreActor.Acquire(probe1.ref)
      probe1.expectMsg(SemaphoreActor.Acquired)

      sem ! SemaphoreActor.Acquire(probe2.ref)
      probe2.expectMsg(SemaphoreActor.Waiting)

      system.stop(probe1.ref)
      probe2.expectMsg(SemaphoreActor.Acquired)
    }

    "Remove the actor from the waiting list if the actor dies" in {
      val probe1 : TestProbe = TestProbe()
      val probe2 : TestProbe = TestProbe()
      val probe3 : TestProbe = TestProbe()

      val sem = TestActorRef[SemaphoreActor]

      sem ! SemaphoreActor.Acquire(probe1.ref)
      probe1.expectMsg(SemaphoreActor.Acquired)

      sem ! SemaphoreActor.Acquire(probe2.ref)
      sem ! SemaphoreActor.Acquire(probe3.ref)
      probe2.expectMsg(SemaphoreActor.Waiting)
      probe3.expectMsg(SemaphoreActor.Waiting)

      system.stop(probe2.ref)
      system.stop(probe1.ref)
      probe3.expectMsg(SemaphoreActor.Acquired)

      sem.underlyingActor.pending should be(empty)
    }
  }
}
