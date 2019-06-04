package blended.jetty.boot.internal

import javax.net.ssl.SSLContext
import domino.DominoActivator
import javax.management.{MBeanServer, ObjectName}
import org.eclipse.jetty.jmx.MBeanContainer
import org.eclipse.jetty.osgi.boot.JettyBootstrapActivator

object JettyActivator {

  var sslContext : Option[SSLContext] = None

}

class JettyActivator extends DominoActivator {

  private val connectorName : ObjectName = new ObjectName("org.eclipse.jetty", "type", "connector")

  whenBundleActive {
    whenServicePresent[MBeanServer] { mbeanServer =>
      whenAdvancedServicePresent[SSLContext]("(type=server)") { sslCtxt =>

        JettyActivator.sslContext = Some(sslCtxt)
        onStop {
          JettyActivator.sslContext = None
        }

        val jettyActivator = new JettyBootstrapActivator()

        jettyActivator.start(bundleContext)

        val mbeanContainer = new MBeanContainer(mbeanServer)
        mbeanServer.registerMBean(mbeanContainer, connectorName)

        onStop {
          jettyActivator.stop(bundleContext)
          mbeanServer.unregisterMBean(connectorName)
        }
      }
    }
  }
}
