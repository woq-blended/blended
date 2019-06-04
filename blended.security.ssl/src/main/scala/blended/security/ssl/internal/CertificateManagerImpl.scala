package blended.security.ssl.internal

import java.io.File
import java.util.Date

import blended.security.ssl.{CertificateManager, CertificateProvider, MemoryKeystore}
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
  override val bundleContext : BundleContext,
  override val capsuleContext : CapsuleContext,
  cfg : CertificateManagerConfig,
  providerMap : Map[String, CertificateProvider]
)
  extends CertificateManager
  with Capsule
  with CapsuleConvenience
  with ServiceProviding {

  private[this] val log = Logger[CertificateManagerImpl]

  private val javaKeystore : Option[JavaKeystore] = cfg.keystoreCfg.map { ksCfg =>
    new JavaKeystore(
      new File(ksCfg.keyStore),
      ksCfg.storePass.toCharArray,
      Some(ksCfg.keyPass.toCharArray)
    )
  }

  private[internal] def registerSslContextProvider() : CapsuleScope = capsuleContext.executeWithinNewCapsuleScope {

    val sslCtxtProvider = new SslContextProvider()

    javaKeystore.map(_.loadKeyStoreFromFile().get).foreach { ks =>
      log.debug("Registering SslContextProvider type=server")
      val serverCtxt : SSLContext = sslCtxtProvider.serverContext(ks, cfg.keystoreCfg.get.keyPass.toCharArray())
      SSLContext.setDefault(serverCtxt)

      serverCtxt.providesService[SSLContext](Map("type" -> "server"))
      log.info(s"Server SSLContext : ${new SslContextInfo(serverCtxt, cfg.validCypherSuites).toString()}")
    }

    sslCtxtProvider.clientContext.providesService[SSLContext](Map("type" -> "client"))
  }

  def start() : Unit = {

    if (!cfg.skipInitialCheck) {
      checkCertificates() match {
        case Failure(e) =>
          log.error("Could not initialise Server certificate(s)")
          throw e

        case Success(None) =>
          registerSslContextProvider()
          log.info("Successfully refreshed trusted certificate store")

        case Success(Some(sks)) =>
          val jks = javaKeystore.get
          //
          //          log.info(s"Successfully obtained [${sks.certificates.size}] Server Certificate(s) for SSLContext")
          //          jks.saveKeyStore(sks) match {
          //            case Failure(t) =>
          //              log.warn(t)(s"Failed to save keystore to file [${jks.keystore.getAbsolutePath()}] : [${t.getMessage()}]")
          //            case Success(mks) =>
          val regScope : Try[CapsuleScope] = Try {
            registerSslContextProvider()
          }

          cfg.refresherConfig match {
            case None => log.debug("No configuration for automatic certificate refresh found")
            case Some(c) =>
              regScope match {
                case Success(scope) =>
                  capsuleContext.addCapsule(new CertificateRefresher(bundleContext, this, c, scope))
                case Failure(t) =>
                  log.warn(s"Failed to load keystore from [${jks.keystore.getAbsolutePath()}] : [${t.getMessage()}]")
              }
          }
        //          }
      }
    } else {
      log.debug("Skipping certificate check and refresher initialization as requested by config value: skipInitialCheck")
    }
  }

  override def stop() : Unit = {}

  private[this] def loadKeyStore() : Try[Option[MemoryKeystore]] = Try {
    javaKeystore.map(_.loadKeyStore().get)
  }

  def nextCertificateTimeout() : Try[Option[Date]] = Try {
    loadKeyStore().get.map(_.nextCertificateTimeout().get)
  }

  /**
   * @return When successful, an updated server keystore
   */
  override def checkCertificates() : Try[Option[MemoryKeystore]] = Try {

    // for all configured providers update the trusted certificates
    if (cfg.maintainTruststore) {
      providerMap.foreach {
        case (key, provider) =>
          provider.rootCertificates().get.foreach { ms =>
            log.info(s"Updating trust store for root certificates of provider [$key]")
            new TrustStoreRefresher(ms).refreshTruststore().get
          }
      }
    }

    // first refresh the server certificates if required
    log.debug("Loading keystore...")
    val ks = loadKeyStore().get
    log.debug(s"Loaded keystore [${ks}]")

    ks.map { ms =>
      log.debug(s"Refreshing certificates for keystore [${ms}]")
      val changedKs = ms.refreshCertificates(cfg.certConfigs, providerMap).get

      log.debug(s"Saving keystore...")
      val jks = javaKeystore.get
      jks.saveKeyStore(changedKs) match {
        case f @ Failure(t) =>
          log.warn(t)(s"Failed to save keystore to file [${jks.keystore.getAbsolutePath()}] : [${t.getMessage()}]")
          throw t
        case Success(ks) => ks
      }
    }
  }
}
