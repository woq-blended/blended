package de.woq.blended.itestsupport.camel

import org.apache.camel.Component
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.slf4j.LoggerFactory

object TestCamelContext {
  def withTestContext(body: TestCamelContext => AnyRef)(implicit testContext: TestCamelContext) {

    try {
      testContext.start()
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
