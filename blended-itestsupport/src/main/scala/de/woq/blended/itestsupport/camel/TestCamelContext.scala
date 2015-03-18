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

package de.woq.blended.itestsupport.camel

import akka.actor.{Actor, ActorLogging}
import org.apache.camel.Component
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.slf4j.LoggerFactory
import akka.event.LoggingReceive
import de.woq.blended.itestsupport.protocol.IntegrationTest

trait TestExecutor[T] extends Actor with ActorLogging {
  
  def test : IntegrationTest[T]
  
  final def receive: Actor.Receive = LoggingReceive {
    case (tc: TestCamelContext) => 
      log.info("-" * 80)
      
      val result = try {
        Right(test(tc))
      } catch {
        case t : Throwable => Left(t)
      } finally {
        //tc.stop()
      }
      
      log.info(s"YYYY : $result")
      sender ! result
  }
}

class TestCamelContext() extends DefaultCamelContext with CamelTestSupport {

  private val log = LoggerFactory.getLogger(classOf[TestCamelContext])

  @throws[Exception]
  def mockEndpoint(name: String) = getEndpoint(s"mock://${name}").asInstanceOf[MockEndpoint]

  def withComponent(compName: String, component: Component) : TestCamelContext = {
    log.debug(s"Adding component [${compName}] to TestCamelContext")
    addComponent(compName, component)
    this
  }

  def withMock(mockName: String, mockUri: String) : TestCamelContext = {
    log.debug(s"Adding mock endpoint and route [${mockName}] to TestCamelContext.")

    addRoutes( new RouteBuilder() {
      override def configure() : Unit = {
        from(mockUri).id(mockName).to(s"mock://${mockName}")
      }
    })
    this
  }
}
