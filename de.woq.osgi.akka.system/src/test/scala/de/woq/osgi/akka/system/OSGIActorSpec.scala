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
import de.woq.osgi.akka.system.internal.OSGIFacade
import akka.actor.{ActorLogging, Actor, Props}
import akka.event.LoggingReceive
import akka.testkit.TestActorRef
import org.osgi.framework.BundleContext
import akka.pattern.{ask,pipe}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout

import protocol._

object OSGIActorDummy {
  def apply()(implicit bundleContext: BundleContext) = new OSGIActorDummy() with OSGIActor
}

class OSGIActorDummy extends Actor with ActorLogging { this : OSGIActor =>
  def receive = LoggingReceive {
    case "invoke" => {
      invokeService[TestInterface1, String](classOf[TestInterface1]) { svc => svc.name } pipeTo(sender)
    }
  }
}

class OSGIActorSpec extends WordSpec
  with Matchers
  with AssertionsForJUnit {

  "OSGIActor" should {

    implicit val timeout = Timeout(1.second)

    "allow to invoke a service" in new TestActorSys with TestSetup with MockitoSugar {
      val facade = system.actorOf(Props(OSGIFacade()), WOQAkkaConstants.osgiFacadePath)

      val probe = TestActorRef(Props(OSGIActorDummy()), "testActor")

      Await.result(probe ?  "invoke", 1.second) match {
        case ServiceResult(Some(s)) => s should be("Andreas")
      }
    }
  }

}
