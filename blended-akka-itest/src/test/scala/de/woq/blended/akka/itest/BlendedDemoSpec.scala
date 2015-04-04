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

package de.woq.blended.akka.itest

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.camel.CamelContext
import org.apache.camel.component.jms.JmsComponent
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.testkit.TestActorRef
import akka.util.Timeout
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.itestsupport.BlendedTestContextManager
import de.woq.blended.itestsupport.ContainerUnderTest
import de.woq.blended.itestsupport.TestContextConfigurator
import de.woq.blended.itestsupport.camel.CamelMockActor
import de.woq.blended.itestsupport.camel.CamelTestSupport
import de.woq.blended.itestsupport.camel.MockAssertions.checkAssertions
import de.woq.blended.itestsupport.camel.MockAssertions.expectedBodies
import de.woq.blended.itestsupport.camel.MockAssertions.expectedMessageCount
import de.woq.blended.itestsupport.condition.Condition
import de.woq.blended.itestsupport.condition.ParallelComposedCondition
import de.woq.blended.itestsupport.docker.protocol.ContainerManagerStopped
import de.woq.blended.itestsupport.docker.protocol.StopContainerManager
import de.woq.blended.itestsupport.jms.JMSAvailableCondition
import de.woq.blended.itestsupport.jolokia.CamelContextExistsCondition
import de.woq.blended.itestsupport.jolokia.JolokiaAvailableCondition
import de.woq.blended.testsupport.TestActorSys
import akka.testkit.TestKit
import org.scalatest.WordSpec
import akka.testkit.ImplicitSender
import akka.testkit.TestProbe
import de.woq.blended.itestsupport.camel.protocol._
import org.scalatest.DoNotDiscover
import scala.concurrent.duration._

@DoNotDiscover
class BlendedDemoSpec(implicit testKit : TestKit) extends WordSpec
  with Matchers
  with BlendedIntegrationTestSupport 
  with CamelTestSupport {

  implicit val system = testKit.system
  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = testKit.system.dispatcher

  private[this] val log = testKit.system.log

  "The demo container" should {

    "Define the sample Camel Route from SampleIn to SampleOut" in {
      
      implicit val timeout : FiniteDuration = 1200.seconds
      
      testContext
      containerReady
      
      val mock = TestActorRef(Props(CamelMockActor("jms:queue:SampleOut")))
      val mockProbe = new TestProbe(system)
      testKit.system.eventStream.subscribe(mockProbe.ref, classOf[MockMessageReceived])
 
      sendTestMessage("Hello Blended!", "jms:queue:SampleIn", false) match {
        // We have successfully sent the message 
        case Right(msg) =>
          // make sure the message reaches the mock actors before we start assertions
          mockProbe.receiveN(1)
          
          checkAssertions(mock, 
            expectedMessageCount(1), 
            expectedBodies("Hello Blended!")
          ) should be(List.empty) 
        // The message has not been sent
        case Left(e) => 
          log.error(e.getMessage, e)
          fail(e.getMessage)
      }
    }
  }
}