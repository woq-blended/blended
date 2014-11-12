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

import org.apache.camel.Component
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.slf4j.LoggerFactory

object TestCamelContext {
  def withTestContext(body: TestCamelContext => Option[Any])(implicit testContext: TestCamelContext) {

    try {
      body(testContext)
    } finally testContext.stop()

  }
}

class TestCamelContext() extends DefaultCamelContext with CamelTestSupport {

  private val log = LoggerFactory.getLogger(classOf[TestCamelContext])

  def mockEndpoint(name: String) = getEndpoint(s"mock://${name}").asInstanceOf[MockEndpoint]

  def withComponent(compName: String, component: Component) = {
    log.debug(s"Adding component [${compName}] to TestCamelContext")
    addComponent(compName, component)
    this
  }

  def withMock(mockName: String, mockUri: String) = {
    log.debug(s"Adding mock endpoint and route [${mockName}] to TestCamelContext.")

    addRoutes( new RouteBuilder() {
      override def configure() {
        from(mockUri).id(mockName).to(s"mock://${mockName}")
      }
    })
    this
  }
}
