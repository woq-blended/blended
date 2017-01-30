package blended.camel.utils

import java.util.concurrent.atomic.AtomicLong

import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext

object BlendedCamelContext {

  val count = new AtomicLong(0)

  def apply() : CamelContext = BlendedCamelContext("blended-" + count.incrementAndGet())

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
