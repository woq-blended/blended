package de.woq.blended.util

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import de.woq.blended.util.protocol._
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
        case info : CounterInfo => {
          info.count == 0 && !info.firstCount.isDefined && !info.lastCount.isDefined
        }
        case _ => false
      }
    }

    "respond with a counter info once it is stopped" in {
      val counter = TestActorRef(Props(TrackingCounter(10.minutes, testActor)))

      counter ! StopCounter

      fishForMessage() {
        case info : CounterInfo => {
          info.count == 0 && !info.firstCount.isDefined && !info.lastCount.isDefined
        }
        case _ => false
      }
    }

    "perform normal count operations" in {
      val counter = TestActorRef(Props(TrackingCounter(10.minutes, testActor)))

      counter ! new IncrementCounter()
      counter ! StopCounter

      fishForMessage() {
        case info : CounterInfo => {
          info.count == 1 && info.interval.length == 0
        }
        case _ => false
      }
    }

    "perform normal stats operations" in {
      val counter = TestActorRef(Props(TrackingCounter(2.seconds, testActor)))

      counter ! new IncrementCounter()
      system.scheduler.scheduleOnce(1.second, counter, new IncrementCounter())
      system.scheduler.scheduleOnce(1.01.seconds, counter, StopCounter)

      fishForMessage(5.seconds) {
        case info : CounterInfo => {
          system.log.info(s"speed is [${info.speed(SECONDS)}/s]")
          info.count == 2 && info.interval.length > 0 && info.speed(SECONDS) > 1.9 && info.speed(SECONDS) < 2.1
        }
        case _ => false
      }
    }
  }

}
