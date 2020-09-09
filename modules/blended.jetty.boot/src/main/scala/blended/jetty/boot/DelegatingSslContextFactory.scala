package blended.jetty.boot

import scala.annotation.nowarn

import blended.jetty.boot.internal.JettyActivator
import blended.util.logging.Logger
import org.eclipse.jetty.util.ssl.SslContextFactory

@nowarn("cat=deprecation")
class DelegatingSslContextFactory extends SslContextFactory {

  private[this] val log = Logger[DelegatingSslContextFactory]

  override def doStart() : Unit = {

    JettyActivator.sslContext.foreach { ctxt =>
      log.info("Injecting external SSL Context to be used in Jetty SSL.")
      setSslContext(ctxt)
    }

    super.doStart()
  }
}
