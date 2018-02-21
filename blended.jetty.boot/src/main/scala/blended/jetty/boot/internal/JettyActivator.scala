package blended.jetty.boot.internal

import javax.net.ssl.SSLContext

import domino.DominoActivator
import org.eclipse.jetty.osgi.boot.JettyBootstrapActivator

object JettyActivator {

  var sslContext: Option[SSLContext] = None

}

class JettyActivator extends DominoActivator {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenAdvancedServicePresent[SSLContext]("(type=server)") { sslCtxt =>

      JettyActivator.sslContext = Some(sslCtxt)
      onStop {
        JettyActivator.sslContext = None
      }

      val jettyActivator = new JettyBootstrapActivator()

      jettyActivator.start(bundleContext)

      onStop {
        jettyActivator.stop(bundleContext)
      }
    }
  }
}
