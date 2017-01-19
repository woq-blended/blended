package blended.camel.utils

import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext

object BlendedCamelContext {

  def apply(name : String) : CamelContext = {

    val result = new DefaultCamelContext()
    result.setName(name)

    val agent = result.getManagementStrategy().getManagementAgent()
    agent.setUseHostIPAddress(true)
    agent.setCreateConnector(false)
    agent.setUsePlatformMBeanServer(true)

    result
  }

}
