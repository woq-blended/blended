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
import de.woq.blended.itestsupport.BlendedTestContext
import de.woq.blended.itestsupport.camel.{CamelTestSupport, TestCamelContext}
import de.woq.blended.testsupport.TestActorSys
import org.apache.camel.component.jms.JmsComponent
import org.scalatest.{DoNotDiscover, Matchers, WordSpecLike}
import scala.concurrent.duration._
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.itestsupport.BlendedTestContextManager
import de.woq.blended.itestsupport.TestContextProvider
import de.woq.blended.itestsupport.ContainerUnderTest
import org.apache.activemq.ActiveMQConnectionFactory
import akka.actor.Props
import scala.util.{Success, Failure}
import de.woq.blended.itestsupport.camel.TestExecutor
import de.woq.blended.itestsupport.protocol.IntegrationTest
import akka.pattern.{pipe, ask}
import scala.concurrent.Await

@DoNotDiscover
class BlendedDemoSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with CamelTestSupport
  with BlendedIntegrationTestSupport {

  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = system.dispatcher

  class TestContainerProxy extends BlendedTestContextManager with TestContextProvider {
    def testContext(cuts: Map[String, ContainerUnderTest]): TestCamelContext = {
      
      val dockerHost = context.system.settings.config.getString("docker.host")
      
      val jmxRest = cuts("blended_demo").url("http", dockerHost, "http")
      val amqUrl = cuts("blended_demo").url("jms", dockerHost, "tcp")
      
      val testContext = new TestCamelContext()
        .withComponent("jms", JmsComponent.jmsComponent(new ActiveMQConnectionFactory(amqUrl.get)))
        
      testContext
    }
  }

  private[this] lazy val log = system.log
  private[this] lazy val ctProxy = system.actorOf(Props(new TestContainerProxy))

  "The demo container" should {

    "Define the sample Camel Route from SampleIn to SampleOut" in {
      
      val test = system.actorOf(Props(new TestExecutor[Int]() {
        
        @throws[Exception]
        override def test : IntegrationTest[Int]= { ctxt =>
          implicit val camelContext = ctxt
 
          ctxt.withMock("sampleOut", "jms:queue:SampleOut")
          ctxt.start()
  
          val mock = ctxt.mockEndpoint("sampleOut")
          mock.reset()
          mock.setExpectedMessageCount(100)
          mock.expectedBodyReceived().constant("Hello Blended!")
  
          ctxt.sendTestMessage("Hello Blended!", "jms:queue:SampleIn", false) match {
            case Right(exchange) =>
              log.info(s"XXXX --- ${mock.getExchanges.size()}")
              mock.assertIsSatisfied(2000l)
              log.info(s"XXXX --- ${mock.getExchanges.size()}")
              exchange.getIn.getBody should be ("Hello Blended!")
            case Left(e) => fail(e)
          }

          Some(0)
        }
        
      }))
      
      val result = (for(
        tc <- testContext(ctProxy);
        r <- (test ? tc.testCamelContext).mapTo[Either[Throwable, Option[Int]]]
      ) yield r)
      
      Await.result(result, 30.seconds) should be (Right(Some(0)))
    }
  }

}
