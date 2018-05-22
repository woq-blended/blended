package blended.util

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import blended.util.protocol._
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class StatsCounterSpec extends TestKit(ActorSystem("StatsCounter"))
  with WordSpecLike
  with Matchers
  with ImplicitSender {

  "The StatsCounter" should {

    "start with count 0" in {

      val counterActor = TestActorRef(Props[StatsCounter])
      val counter = counterActor.underlyingActor.asInstanceOf[StatsCounter]

      counter.count should be(0)
    }

    "increment the counter when an Increment message is received" in {
      val counterActor = TestActorRef(Props[StatsCounter])
      val counter = counterActor.underlyingActor.asInstanceOf[StatsCounter]

      counter.receive(new IncrementCounter)
      counter.count should be(1)

      counter.receive(new IncrementCounter(10))
      counter.count should be (11)

      counter.receive(new IncrementCounter(-1))
      counter.count should be (10)
    }

    "keep track of the first and last time the count has changed" in {
      val counterActor = TestActorRef(Props[StatsCounter])
      val counter = counterActor.underlyingActor.asInstanceOf[StatsCounter]

      counter.receive(new IncrementCounter)

      counter.firstCount should be (defined)
      counter.lastCount should be (defined)

      counter.firstCount should be (counter.lastCount)
    }

    "Start with an uninitialized interval" in {

      implicit val timeout = Timeout(3.seconds)

      val counterActor = TestActorRef(Props[StatsCounter])

      val info = Await.result( (counterActor ? QueryCounter).mapTo[CounterInfo], 3.seconds )

      info.count should be (0)
      info.interval.length should be (0)
      info.speed() should be (0.0)
    }

    "have the max speed after sending only on counter message" in {
      implicit val timeout = Timeout(3.seconds)
      val counterActor = TestActorRef(Props[StatsCounter])
      counterActor ! new IncrementCounter

      val info = Await.result( (counterActor ? QueryCounter).mapTo[CounterInfo], 3.seconds )

      info.count should be (1)
      info.interval.length should be (0)
      info.speed() should be (Double.MaxValue)
    }

    "track the throughput as count / time unit" in {

      implicit val ctxt = system.dispatcher

      val counterActor = TestActorRef(Props[StatsCounter])

      counterActor ! new IncrementCounter
      system.scheduler.scheduleOnce(1.second, counterActor, new IncrementCounter)
      system.scheduler.scheduleOnce(1.1.seconds, counterActor, QueryCounter)

      fishForMessage() {
        case info : CounterInfo =>
          system.log.info(s"Speed is [${info.speed(SECONDS)} / s]")
          info.count == 2 && info.interval.length > 0 && (info.speed(SECONDS) > 1.5) && (info.speed(SECONDS) < 2.5)
      }
    }

  }
}
