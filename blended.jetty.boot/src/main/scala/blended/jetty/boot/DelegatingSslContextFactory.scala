package blended.jetty.boot

import blended.jetty.boot.internal.JettyActivator
import org.eclipse.jetty.util.ssl.SslContextFactory

class DelegatingSslContextFactory extends SslContextFactory {

  private[this] val log = org.log4s.getLogger

  override def doStart(): Unit = {

    JettyActivator.sslContext.foreach { ctxt =>
      log.info("Injecting external SSL Context to be used in Jetty SSL.")
      setSslContext(ctxt)
    }

    super.doStart()
  }
}
