package de.woq.blended.itestsupport.camel

import org.apache.camel.Component
import org.apache.camel.impl.DefaultCamelContext

trait CamelContextProvider {

  lazy val camelContext = {
    val context = new DefaultCamelContext()
    camelComponents.keys.foreach(compName => context.addComponent(compName, camelComponents(compName)))
    context.start()
    context
  }

  val  camelComponents : Map[String, Component] = Map.empty

}
