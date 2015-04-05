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

import akka.actor.Props
import akka.testkit.TestProbe
import de.wayofquality.blended.akka.internal.OfflineServiceTracker
import de.wayofquality.blended.akka.protocol.{TrackerAddingService, Service, CreateReference}
import de.wayofquality.blended.testsupport.TestActorSys
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class OfflineServiceTrackerSpec extends WordSpec
  with Matchers
  with AssertionsForJUnit {

  "OfflineServiceTracker" should {

    "respond with the dead letter actor when no service came online within a given time frame" in new TestActorSys with TestSetup with MockitoSugar {
      val references = TestProbe()
      val ost = system.actorOf(Props(OfflineServiceTracker[TestInterface1](references.ref)), "references")
      ost ! CreateReference(classOf[TestInterface1])
      expectMsg(Service(system.deadLetters))
    }

    "respond with the service actor if a service came online within a given time frame" in new TestActorSys with TestSetup with MockitoSugar {
      val references = TestProbe()
      val ost = system.actorOf(Props(OfflineServiceTracker[TestInterface1](references.ref)))
      ost ! CreateReference(classOf[TestInterface1])
      ost ! TrackerAddingService(svcRef, service)

      references.expectMsg(OfflineServiceTracker.ReferenceAdded[TestInterface1](self, svcRef))
    }
  }

}
