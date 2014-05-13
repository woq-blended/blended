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

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.slf4j.LoggerFactory
import org.mockito.Mockito.verify
import de.woq.osgi.akka.system.internal.OSGIServiceReference

import protocol._

class OSGIServiceReferenceSpec extends TestKit(ActorSystem("OSGIServiceRef"))
  with WordSpecLike
  with Matchers
  with AssertionsForJUnit
  with BeforeAndAfterAll
  with ImplicitSender {

  val log = LoggerFactory.getLogger(classOf[OSGIServiceReferenceSpec])

  "OSGIServiceReference" should {

    "allow to invoke the underlying Service" in new TestSetup with MockitoSugar {

      val testActor = TestActorRef(Props(OSGIServiceReference(svcRef)))
      testActor ! InvokeService { svc: TestInterface1 => svc.name }

      expectMsgAllClassOf(classOf[ServiceResult[String]]) foreach { m=>
        m.result should not be (None)
        m.result.get should be ("Andreas")
      }
    }

    "allow to unget the underlying service reference" in new TestSetup with MockitoSugar {
      val testActor = TestActorRef(Props(OSGIServiceReference(svcRef)))
      testActor ! UngetServiceReference

      verify(osgiContext).ungetService(svcRef)
    }
  }
}
