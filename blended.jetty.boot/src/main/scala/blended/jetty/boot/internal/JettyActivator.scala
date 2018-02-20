package blended.jetty.boot.internal

import javax.net.ssl.SSLContext

import domino.DominoActivator
import org.eclipse.jetty.osgi.boot.JettyBootstrapActivator

class JettyActivator extends DominoActivator {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenAdvancedServicePresent[SSLContext]("(type=server)") { sslCtxt =>
      val jettyActivator = new JettyBootstrapActivator()

      log.debug("mine :" + this.getClass().getClassLoader())
      log.debug("jetty:" + classOf[JettyBootstrapActivator].getClassLoader)

      jettyActivator.start(bundleContext)

      onStop {
        jettyActivator.stop(bundleContext)
      }
    }
  }
}
