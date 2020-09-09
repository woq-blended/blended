package blended.jetty.boot.internal

import blended.util.logging.Logger
import domino.DominoActivator
import javax.net.ssl.SSLContext
import org.eclipse.jetty.osgi.boot.JettyBootstrapActivator

import scala.util.control.NonFatal

object JettyActivator {
  var sslContext : Option[SSLContext] = None
}

class JettyActivator extends DominoActivator {

  private val log : Logger = Logger[JettyActivator]

  whenBundleActive {
    whenAdvancedServicePresent[SSLContext]("(type=server)") { sslCtxt =>

      JettyActivator.sslContext = Some(sslCtxt)
      onStop {
        JettyActivator.sslContext = None
      }

      val jettyActivator = new JettyBootstrapActivator()

      try {
        log.info(s"Starting Jetty HTTP Server ...")
        jettyActivator.start(bundleContext)
      } catch {
        case NonFatal(e) =>
          log.warn(s"Failed to start Jetty Http Server : ${e.getMessage()}")
      }

      onStop {
        jettyActivator.stop(bundleContext)
      }
    }
  }
}
