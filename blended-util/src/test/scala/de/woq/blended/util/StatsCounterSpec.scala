/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.blended.util

import akka.actor.{Props, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import org.scalatest.{WordSpecLike, Matchers}
import akka.pattern.ask
import scala.concurrent.duration._

import de.woq.blended.util.protocol._

import scala.concurrent.Await

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

      implicit val timeout = Timeout(3.seconds)
      implicit val ctxt = system.dispatcher

      val counterActor = TestActorRef(Props[StatsCounter])

      counterActor ! new IncrementCounter
      system.scheduler.scheduleOnce(1.second, counterActor, new IncrementCounter)
      system.scheduler.scheduleOnce(1.01.seconds, counterActor, QueryCounter)

      fishForMessage(3.seconds) {
        case info : CounterInfo => {
          system.log.info(s"Speed is [${info.speed(SECONDS)} / s]")
          info.count == 2 && info.interval.length > 0 && (info.speed(SECONDS) > 1.9) && (info.speed(SECONDS) < 2.1)
        }
        case _ => false
      }
    }

  }
}
