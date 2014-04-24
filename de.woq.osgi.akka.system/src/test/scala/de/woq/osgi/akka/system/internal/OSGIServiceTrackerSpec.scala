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

package de.woq.osgi.akka.system.internal

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{Terminated, ActorRef, Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import de.woq.osgi.akka.system.{OSGIProtocol, TestInterface1, TestSetup}
import akka.event.Logging.Info
import org.osgi.framework.BundleContext
import OSGIProtocol.{TrackerClose, TrackerRemovedService, TrackerModifiedService, TrackerAddingService}
import de.woq.osgi.java.testsupport.TestActorSys

class OSGIServiceTrackerSpec extends TestKit(ActorSystem("OSGITracker"))
  with WordSpecLike
  with Matchers
  with AssertionsForJUnit
  with BeforeAndAfterAll
  with ImplicitSender
  with TestSetup
  with MockitoSugar {

  "OSGIServiceTracker" should {

    implicit val bundleContext = osgiContext

    def testTrackerAdapter[I <: AnyRef](observer: ActorRef)(implicit osgiContext : BundleContext) =
      new TrackerAdapter[I] with BundleContextProvider {
        override val trackerObserver: ActorRef = observer
        override implicit val bundleContext: BundleContext = osgiContext
      }

    "allow to setup an OSGI Servicetracker" in new TestActorSys() {

      system.eventStream.subscribe(self, classOf[Info])

      TestActorRef(Props(OSGIServiceTracker(classOf[TestInterface1], self, testTrackerAdapter(self))), "Tracker")

      fishForMessage() {
        case Info(_,_,m) => m.toString.startsWith("Initialized Service Tracker")
        case _ => false
      }
    }

    "notify the observer when a service reference is added" in new TestActorSys() {

      val adapter = testTrackerAdapter[TestInterface1](self)

      TestActorRef(Props(OSGIServiceTracker(classOf[TestInterface1], self, testTrackerAdapter(self))), "Tracker")

      adapter.addingService(svcRef) should be (service)

      fishForMessage() {
        case TrackerAddingService(svcRef, service) => true
        case _ => false
      }
    }

    "notify the observer when a service reference is removed" in new TestActorSys() {

      val adapter = testTrackerAdapter[TestInterface1](self)
      TestActorRef(Props(OSGIServiceTracker(classOf[TestInterface1], self, testTrackerAdapter(self))), "Tracker")

      adapter.removedService(svcRef, service)

      fishForMessage() {
        case TrackerRemovedService(svcRef, service) => true
        case _ => false
      }
    }

    "notify the observer when a service reference is modified" in new TestActorSys() {

      val adapter = testTrackerAdapter[TestInterface1](self)
      TestActorRef(Props(OSGIServiceTracker(classOf[TestInterface1], self, testTrackerAdapter(self))), "Tracker")

      adapter.modifiedService(svcRef, service)

      fishForMessage() {
        case TrackerModifiedService(svcRef, service) => true
        case _ => false
      }
    }

    "die when the tracker is closed" in {

      val tracker = TestActorRef(Props(OSGIServiceTracker(classOf[TestInterface1], self, testTrackerAdapter(self))), "Tracker")
      watch(tracker)

      tracker ! TrackerClose

      fishForMessage() {
        case Terminated(tracker) => true
      }
    }
  }

}
