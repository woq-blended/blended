package de.woq.blended.itestsupport.camel

import org.apache.camel.Component
import org.apache.camel.impl.DefaultCamelContext

trait CamelContextProvider {

  final lazy val camelContext = {
    val context = new DefaultCamelContext()
    context
  }

  final def addCompoments {
    camelComponents.keys.foreach(compName => camelContext.addComponent(compName, camelComponents(compName)))
  }

  final def startContext {
    camelContext.start()
  }

  lazy val camelComponents : Map[String, Component] = Map.empty

}
