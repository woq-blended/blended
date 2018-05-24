package blended.camel.utils

import java.util.concurrent.atomic.AtomicLong

import blended.container.context.api.ContainerIdentifierService
import org.apache.camel.CamelContext
import org.apache.camel.impl.{DefaultCamelContext, PropertyPlaceholderDelegateRegistry, SimpleRegistry}

object BlendedCamelContextFactory {

  private val count = new AtomicLong(0)

  def createContext(
    name : String = "blended-" + BlendedCamelContextFactory.count.incrementAndGet(),
    withJmx: Boolean = true
  ) : CamelContext =
    (new BlendedCamelContextFactory with CamelContextPropertyProvider).createContext(name, withJmx)

  def createContext(
    name : String,
    withJmx: Boolean,
    idSvc : ContainerIdentifierService
  ) =
    new BlendedCamelContextFactory with IdServiceCamelContextPropertyProvider {
      override def idService = idSvc
    }.createContext(name, withJmx)
}

class BlendedCamelContextFactory { this: CamelContextPropertyProvider =>

  def createContext(
    name : String,
    withJmx: Boolean
  ) : CamelContext = {

    val result = new DefaultCamelContext(new SimpleRegistry())
    result.setName(name)

    val agent = result.getManagementStrategy().getManagementAgent()
    agent.setUseHostIPAddress(true)
    agent.setCreateConnector(false)
    agent.setUsePlatformMBeanServer(true)

    if (!withJmx) {
      result.disableJMX()
    }

    contextProperties.foreach{ case (k,v) => result.getProperties.put(k,v) }
    result
  }
}
