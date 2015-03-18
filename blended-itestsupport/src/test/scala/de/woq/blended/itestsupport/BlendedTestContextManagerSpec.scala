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

package de.woq.blended.itestsupport

import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.mock.MockitoSugar
import akka.testkit.TestProbe
import de.woq.blended.itestsupport.camel.TestCamelContext
import de.woq.blended.testsupport.TestActorSys
import akka.testkit.TestActorRef
import akka.actor.Props
import scala.concurrent.Await
import akka.pattern.ask
import de.woq.blended.itestsupport.protocol.TestContextRequest
import akka.util.Timeout
import scala.concurrent.duration._
import de.woq.blended.itestsupport.docker.DockerTestSetup

class BlendedTestContextManagerSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {
  
  val tc = mock[TestCamelContext]
  
  class TestContainerProxy extends BlendedTestContextManager with TestContextProvider {
    def testContext(cuts: Map[String, ContainerUnderTest]): TestCamelContext = tc
  }
  
  "The TestContextManager" should {
    
    "return a BlendedTestContext once it is initialized" in {
      
      implicit val timeout = Timeout(3.seconds)
      
      val probe = new TestProbe(system)
      system.eventStream.subscribe(probe.ref, classOf[BlendedTestContext])
      
      val mgr = TestActorRef(Props(new TestContainerProxy), "proxy")
      
      val context = Await.result(mgr ? TestContextRequest(ContainerUnderTest.containerMap(system.settings.config)), 3.seconds)
    
    }
  }

}