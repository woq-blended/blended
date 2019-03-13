package blended.security.ssl.internal

import java.io.File
import java.util.Date

import blended.security.ssl.{CertificateManager, CertificateProvider}
import blended.util.logging.Logger
import domino.capsule._
import domino.service_providing.ServiceProviding
import javax.net.ssl.SSLContext
import org.osgi.framework.BundleContext

import scala.util.{Failure, Success, Try}

/**
 * A class to manage one or more server side certificates within a given keystore
 * to be used as SSL server certificates.
 */
class CertificateManagerImpl(
  override val bundleContext: BundleContext,
  override val capsuleContext: CapsuleContext,
  cfg: CertificateManagerConfig,
  providerMap: Map[String, CertificateProvider]
)
  extends CertificateManager
  with Capsule
  with CapsuleConvenience
  with ServiceProviding {

  private[this] val log = Logger[CertificateManagerImpl]

  private[this] lazy val javaKeystore : JavaKeystore= new JavaKeystore(
    new File(cfg.keyStore),
    cfg.storePass.toCharArray,
    Some(cfg.keyPass.toCharArray)
  )

  private[internal] def registerSslContextProvider(): CapsuleScope = capsuleContext.executeWithinNewCapsuleScope {
    log.debug("Registering SslContextProvider type=client and type=server")
    val sslCtxtProvider = new SslContextProvider(javaKeystore.loadKeyStoreFromFile().get, cfg.keyPass.toCharArray)
    // TODO: what should we do with this side-effect, if we unregister the context provider?
    // FIXME: should this side-effect be configurable?

    log.info(s"Server SSLContext : ${new SslContextInfo(sslCtxtProvider.serverContext, cfg.validCypherSuites).toString()}")

    SSLContext.setDefault(sslCtxtProvider.serverContext)
    sslCtxtProvider.clientContext.providesService[SSLContext](Map("type" -> "client"))
    sslCtxtProvider.serverContext.providesService[SSLContext](Map("type" -> "server"))
  }

  def start(): Unit = {

    if (!cfg.skipInitialCheck) {
      checkCertificates() match {
        case Failure(e) =>
          log.error("Could not initialise Server certificate(s)")
          throw e

        case Success(sks) =>
          log.info(s"Successfully obtained [${sks.certificates.size}] Server Certificate(s) for SSLContext")
          javaKeystore.saveKeyStore(sks) match {
            case Failure(t) =>
              log.warn(s"Failed to save keystore to file [${cfg.keyStore}] : [${t.getMessage()}]")
            case Success(mks) =>
              val regScope : Try[CapsuleScope] = Try { registerSslContextProvider() }

              cfg.refresherConfig match {
                case None => log.debug("No configuration for automatic certificate refresh found")
                case Some(c) =>
                  regScope match {
                    case Success(scope) =>
                      capsuleContext.addCapsule(new CertificateRefresher(bundleContext, this, c, scope))
                    case Failure(t) =>
                      log.warn(s"Failed to load keystore from [${cfg.keyStore}] : [${t.getMessage()}]")
                  }
              }
          }
      }
    } else {
      log.debug("Skipping certificate check and refresher initialization as requested by config value: skipInitialCheck")
    }
  }

  override def stop(): Unit = {}

  private[this] def loadKeyStore(): Try[MemoryKeystore] = javaKeystore.loadKeyStore()

  def nextCertificateTimeout() : Try[Date] = javaKeystore.loadKeyStore().get.nextCertificateTimeout()

  /**
   * @return When successful, a tuple of keystore and a list of updated certificate aliases, else the failure.
   */
  override def checkCertificates(): Try[MemoryKeystore] = Try {
    val ms : MemoryKeystore = loadKeyStore().get
    ms.refreshCertificates(cfg.certConfigs, providerMap).get
  }
}
