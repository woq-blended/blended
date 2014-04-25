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

import de.woq.osgi.java.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import akka.actor.Props
import de.woq.osgi.akka.system.internal.{OSGIReferences, OSGIFacade}
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.junit.AssertionsForJUnit

class OSGIReferencesSpec extends WordSpec
  with Matchers
  with AssertionsForJUnit {

  "Allow to retrieve a service reference" in new TestActorSys with TestSetup with MockitoSugar {
  }

  "return the dead letter Actor for service lookups when the service does not appear in a timely manner" in
    new TestActorSys with TestSetup with MockitoSugar {

      apply {
        val facade = system.actorOf(Props(OSGIFacade()), "facade")
        facade ! OSGIProtocol.GetService(classOf[TestInterface2])
        expectMsgAllClassOf(classOf[OSGIProtocol.Service]) foreach { m =>
          m.service should be (system.deadLetters)
        }
      }
    }

}
