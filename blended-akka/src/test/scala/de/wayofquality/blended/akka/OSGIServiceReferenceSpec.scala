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

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import de.wayofquality.blended.akka.internal.OSGIServiceReference
import de.wayofquality.blended.akka.protocol._
import org.mockito.Mockito.verify
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory

class OSGIServiceReferenceSpec extends TestKit(ActorSystem("OSGIServiceRef"))
  with WordSpecLike
  with Matchers
  with AssertionsForJUnit
  with BeforeAndAfterAll
  with ImplicitSender
  with TestSetup
  with MockitoSugar {

  val log = LoggerFactory.getLogger(classOf[OSGIServiceReferenceSpec])

  "OSGIServiceReference" should {

    "allow to invoke the underlying Service" in {

      val testActor = TestActorRef(Props(OSGIServiceReference(svcRef)))
      testActor ! InvokeService { svc: TestInterface1 => svc.name }

      expectMsgAllClassOf(classOf[ServiceResult[String]]) foreach { m=>
        m.result should not be (None)
        m.result.get should be ("Andreas")
      }
    }

    "allow to unget the underlying service reference" in {
      val testActor = TestActorRef(Props(OSGIServiceReference(svcRef)))
      testActor ! UngetServiceReference

      verify(osgiContext).ungetService(svcRef)
    }
  }
}
