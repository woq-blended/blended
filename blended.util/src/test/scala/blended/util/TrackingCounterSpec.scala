package blended.util

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import blended.util.protocol._
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._

class TrackingCounterSpec extends TestKit(ActorSystem("TrackingCounterSpec"))
  with WordSpecLike
  with Matchers
  with ImplicitSender {

  implicit val ctxt = system.dispatcher

  "A tracking counter" should {

    "send a Counter Info after it has timed out" in {

      val counter = TestActorRef(Props(TrackingCounter(10.millis, testActor)))

      fishForMessage() {
        case info : CounterInfo =>
          info.count == 0 && !info.firstCount.isDefined && !info.lastCount.isDefined
      }
    }

    "respond with a counter info once it is stopped" in {
      val counter = TestActorRef(Props(TrackingCounter(10.minutes, testActor)))

      counter ! StopCounter

      fishForMessage() {
        case info : CounterInfo =>
          info.count == 0 && !info.firstCount.isDefined && !info.lastCount.isDefined
      }
    }

    "perform normal count operations" in {
      val counter = TestActorRef(Props(TrackingCounter(10.minutes, testActor)))

      counter ! new IncrementCounter()
      counter ! StopCounter

      fishForMessage() {
        case info : CounterInfo =>
          info.count == 1 && info.interval.length == 0
      }
    }

    "perform normal stats operations" in {
      val counter = TestActorRef(Props(TrackingCounter(2.seconds, testActor)))

      counter ! new IncrementCounter()
      system.scheduler.scheduleOnce(1.second, counter, new IncrementCounter())
      system.scheduler.scheduleOnce(1.01.seconds, counter, StopCounter)

      fishForMessage(5.seconds) {
        case info : CounterInfo =>
          system.log.info(s"speed is [${info.speed(SECONDS)}/s]")
          info.count == 2 && info.interval.length > 0 && info.speed(SECONDS) > 1.9 && info.speed(SECONDS) < 2.1
      }
    }
  }

}
