package blended.activemq.client.internal

import blended.activemq.client.VerificationFailedHandler
import blended.jms.utils.IdAwareConnectionFactory
import blended.util.logging.Logger
import org.osgi.framework.BundleContext

class DefaultVerificationFailedHandler(bundleContext : BundleContext) extends VerificationFailedHandler {

  private val log : Logger = Logger[DefaultVerificationFailedHandler]

  override def verificationFailed(cf: IdAwareConnectionFactory): Unit = {

    log.error(s"Verification for connection [${cf.vendor}:${cf.provider}] has failed. Shutting down container ...")
    bundleContext.getBundle(0).stop()
  }
}
