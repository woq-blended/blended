package de.woq.blended.itestsupport.camel

import org.apache.camel.Component
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.scalatest.fixture

class TestCamelContext(components: Map[String, Component], mocks: Map[String, String]) extends fixture.NoArg {

  implicit  val camelContext = new DefaultCamelContext()

  components.keys.foreach{ compName => camelContext.addComponent(compName, components(compName)) }

  mocks.keys.foreach{ mockName =>
    camelContext.addRoutes( new RouteBuilder() {
      override def configure() {
        from(mocks(mockName)).id(mockName).to(s"mock://${mockName}")
      }
    })
  }

  override def apply() {
    try super.apply()
    finally camelContext.stop()
  }

  def mockEndpoint(name: String) = camelContext.getEndpoint(s"mock://${name}").asInstanceOf[MockEndpoint]
}
