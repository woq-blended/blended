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

import javax.jms.ConnectionFactory
import akka.util.Timeout
import de.woq.blended.testsupport.TestActorSys
import org.apache.camel.component.jms.JmsComponent
import org.scalatest.{DoNotDiscover, Matchers, WordSpecLike}
import scala.concurrent.duration._
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.itestsupport.BlendedTestContextManager
import de.woq.blended.itestsupport.ContainerUnderTest
import org.apache.activemq.ActiveMQConnectionFactory
import akka.actor.Props
import scala.util.{Success, Failure}
import akka.pattern.{pipe, ask}
import scala.concurrent.Await
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.camel.CamelMockActor
import de.woq.blended.itestsupport.camel.CamelTestSupport
import de.woq.blended.itestsupport.ContainerUnderTest
import de.woq.blended.itestsupport.BlendedTestContextManager
import de.woq.blended.itestsupport.camel.CamelMockActor
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.itestsupport.TestContextConfigurator
import org.apache.camel.CamelContext
import de.woq.blended.itestsupport.camel.MockAssertions._
import de.woq.blended.itestsupport.camel.protocol._

@DoNotDiscover
class BlendedDemoSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with CamelTestSupport
  with BlendedIntegrationTestSupport {

  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = system.dispatcher

  class TestContainerProxy extends BlendedTestContextManager with TestContextConfigurator {
    def configure(cuts: Map[String, ContainerUnderTest], camelCtxt : CamelContext): CamelContext = {
      
      val dockerHost = context.system.settings.config.getString("docker.host")
      
      val jmxRest = cuts("blended_demo").url("http", dockerHost, "http")
      val amqUrl = cuts("blended_demo").url("jms", dockerHost, "tcp")
      camelCtxt.addComponent("jms", JmsComponent.jmsComponent(new ActiveMQConnectionFactory(amqUrl.get)))
      camelCtxt
    }
  }

  private[this] lazy val log = system.log
  private[this] lazy val ctProxy = system.actorOf(Props(new TestContainerProxy))

  "The demo container" should {

    "Define the sample Camel Route from SampleIn to SampleOut" in {
      
      Await.result(testContext(ctProxy), 3.seconds)
      
      val mock = TestActorRef(Props(CamelMockActor("jms:queue:SampleOut")))
 
      sendTestMessage("Hello Blended!", "jms:queue:SampleIn", false) match {
        case Right(msg) =>
          // make sure the message reaches the mock actors before we start assertions
          mockProbe.receiveN(1)
          mock ! CheckAssertions(expectedMessageCount(1))
          // Eventually the mock will answer with the checkresults
          val r = errors(receiveN(1).toList.head.asInstanceOf[CheckResults])
          log.info(prettyPrint(r))
          r should be (List.empty)        
        case Left(e) => 
          log.error(e.getMessage, e)
          fail(e.getMessage)
      }
    }
  }
}