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

package de.woq.blended.akka

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.pattern.{ask, pipe}
import akka.testkit.TestActorRef
import akka.util.Timeout
import de.woq.blended.akka.protocol.ServiceResult
import de.woq.blended.testsupport.TestActorSys
import de.woq.blended.akka.internal.OSGIFacade
import org.osgi.framework.BundleContext
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

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
      val facade = system.actorOf(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)

      val probe = TestActorRef(Props(OSGIActorDummy()), "testActor")

      Await.result(probe ?  "invoke", 1.second) match {
        case ServiceResult(Some(s)) => s should be("Andreas")
      }
    }
  }

}
