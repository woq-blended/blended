/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.akka.system

import org.scalatest.{Matchers, WordSpec}
import org.scalatest.junit.AssertionsForJUnit
import de.woq.osgi.java.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import akka.testkit.TestProbe
import de.woq.osgi.akka.system.internal.OfflineServiceTracker
import akka.actor.Props

import protocol._

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
