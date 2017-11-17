package blended.camel.utils

import java.util.concurrent.atomic.AtomicLong

import blended.container.context.ContainerIdentifierService
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext

import scala.collection.JavaConverters._

object BlendedCamelContext {

  val propKey = "blended.camel.context.properties"

  val count = new AtomicLong(0)

  def apply() : CamelContext = BlendedCamelContext(true)

  def apply(withJmx: Boolean) : CamelContext = BlendedCamelContext(
    name = "blended-" + count.incrementAndGet(),
    withJmx = withJmx,
    idSvc = None
  )

  def apply(
    name : String,
    withJmx: Boolean,
    idSvc : Option[ContainerIdentifierService]
  ) : CamelContext = {

    val result = new DefaultCamelContext()
    result.setName(name)

    val agent = result.getManagementStrategy().getManagementAgent()
    agent.setUseHostIPAddress(true)
    agent.setCreateConnector(false)
    agent.setUsePlatformMBeanServer(true)

    if (!withJmx) {
      result.disableJMX()
    }

    val cfg = idSvc match {
      case None => ConfigFactory.empty()
      case Some(svc) => svc.containerContext.getContainerConfig()
    }

    if (cfg.hasPath(propKey)) {

      val props = cfg.getConfig(propKey)

      props.entrySet().asScala.map { entry =>
        result.getProperties().put(entry.getKey(), props.getString(entry.getKey()))
      }
    }
    result
  }
}
