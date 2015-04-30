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

package de.wayofquality.blended.akka

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill}
import akka.testkit.{TestActorRef, TestKit}
import de.wayofquality.blended.akka.protocol._
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
