package blended.security.ssl.internal

import java.io.{ File, FileInputStream, FileOutputStream }
import java.security.{ KeyPair, KeyStore, PrivateKey }
import java.util.Date

import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

import blended.security.ssl.{ CertificateManager, CertificateProvider, CertificateHolder, X509CertificateInfo }
import blended.security.ssl.CertificateManager
import blended.util.logging.Logger
import domino.capsule._
import domino.service_providing.ServiceProviding
import javax.net.ssl.SSLContext
import org.osgi.framework.BundleContext

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
  private[this] val millisPerDay = 1.day.toMillis

  private[this] lazy val keyStore = loadKeyStore()

  def getKeystore(): ServerKeyStore = keyStore.get

  private[internal] def registerSslContextProvider(ks: KeyStore): CapsuleScope = capsuleContext.executeWithinNewCapsuleScope {
    log.debug("Registering SslContextProvider type=client and type=server")
    val sslCtxtProvider = new SslContextProvider(ks, cfg.keyPass.toCharArray)
    // TODO: what should we do with this side-effect, if we unregister the context provider?
    // FIXME: should this side-effect be configurable?
    SSLContext.setDefault(sslCtxtProvider.serverContext)
    val serverReg = sslCtxtProvider.clientContext.providesService[SSLContext](Map("type" -> "client"))
    val clientReg = sslCtxtProvider.serverContext.providesService[SSLContext](Map("type" -> "server"))

    onStop {
      log.debug("Unregistering SslContextProvider type=client and type=server")
      Try { serverReg.unregister() }
      Try { clientReg.unregister() }
    }
  }

  def start(): Unit = {

    if (!cfg.skipInitialCheck) {
      checkCertificates() match {
        case Failure(e) =>
          log.error("Could not initialise Server certificate(s)")
          throw e

        case Success((sks, _)) =>
          log.info("Successfully obtained Server Certificate(s) for SSLContext")
          val regScope = registerSslContextProvider(sks.keyStore)

          cfg.refresherConfig match {
            case None => log.debug("No configuration for automatic certificate refresh found")
            case Some(c) =>
              capsuleContext.addCapsule(new CertificateRefresher(bundleContext, this, c, regScope))
          }
      }
    } else {
      log.debug("Skipping certificate check and refresher initialization as requested by config value: skipInitialCheck")
    }
  }

  override def stop(): Unit = {}

  def nextCertificateTimeout(): Date = if (getKeystore().serverCertificates.values.isEmpty)
    new Date()
  else
    getKeystore().serverCertificates.values.map(_.chain.head.getNotAfter).min

  private[this] def loadKeyStore(): Try[ServerKeyStore] = {

    log.info(s"Initializing key store [${cfg.keyStore}] for server certificate(s) ...")

    val ks = KeyStore.getInstance("PKCS12")
    val f = new File(cfg.keyStore)

    if (f.exists()) {
      val fis = new FileInputStream(f)
      try {
        ks.load(fis, cfg.storePass.toCharArray)
      } finally {
        fis.close()
      }
    } else {
      log.info(s"Creating empty key store  ...")
      ks.load(null, cfg.storePass.toCharArray)
      saveKeyStore(ks)
    }

    serverKeystore(ks)
  }

  /**
   * @return When successful, a tuple of keystore and a list of updated certificate aliases, else the failure.
   */
  override def checkCertificates(): Try[(ServerKeyStore, List[String])] = Try {

    val ks = keyStore.get

    def changedAliases(certConfigs: List[CertificateConfig], changed: List[String]): Try[List[String]] = Try {
      certConfigs match {
        case Nil => changed
        case head :: tail =>
          val existingCert = extractServerCertificate(ks.keyStore, head).get
          existingCert match {
            case Some(serverCertificate) =>
              val certInfo = X509CertificateInfo(serverCertificate.chain.head)
              val remaining = certInfo.notAfter.getTime() - System.currentTimeMillis()

              if (remaining <= head.minValidDays * millisPerDay) {
                log.info(s"Certificate [${head.alias}] is about to expire in ${remaining.toDouble / millisPerDay} days...refreshing certificate")
                updateKeystore(ks.keyStore, existingCert, head).recoverWith {
                  case _: Throwable =>
                    log.info(s"Could not refresh certificate [${head.alias}], reusing the existing one.")
                    changedAliases(tail, changed)
                }
                changedAliases(tail, head.alias :: changed).get
              } else {
                log.info(s"Server certificate [${head.alias}] is still valid.")
                changedAliases(tail, changed).get
              }
            case None =>
              log.info(s"Certificate with alias [${head.alias}] does not yet exist.")
              updateKeystore(ks.keyStore, None, head).get
              changedAliases(tail, head.alias :: changed).get
          }
      }
    }

    (ks, changedAliases(cfg.certConfigs, List.empty).get)
  }

  private[this] def updateKeystore(ks: KeyStore, existingCert: Option[CertificateHolder], certCfg: CertificateConfig): Try[ServerKeyStore] = Try {
    log.info(s"Aquiring new certificate from certificate provider [${certCfg.provider}]")

    val provider = providerMap.get(certCfg.provider).get
    val newCert = provider.refreshCertificate(existingCert, certCfg.cnProvider)

    newCert match {
      case Failure(e) =>
        log.error(e)("Could not update keystore")
        throw e
      case Success(cert) =>
        val info = X509CertificateInfo(cert.chain.head)
        log.info(s"Successfully obtained certificate from certificate provider [$provider] : $info")
        cert.keyPair.foreach(p => ks.setKeyEntry(certCfg.alias, p.getPrivate(), cfg.keyPass.toCharArray, cert.chain.toArray))
        saveKeyStore(ks).get
        serverKeystore(ks).get
    }
  }

  private[this] def saveKeyStore(ks: KeyStore): Try[KeyStore] = Try {
    val fos = new FileOutputStream(cfg.keyStore)
    try {
      ks.store(fos, cfg.storePass.toCharArray)
      log.info(s"Successfully written modified key store to [${cfg.keyStore}] with storePass [${cfg.storePass}]")
    } finally {
      fos.close()
    }

    ks
  }

  // Extract a single server certificate from the underlying keystore
  private[this] def extractServerCertificate(ks: KeyStore, certCfg: CertificateConfig): Try[Option[CertificateHolder]] = Try {
    Option(ks.getCertificateChain(certCfg.alias)).map { chain =>
      val e = ks.getCertificate(certCfg.alias)
      val key = ks.getKey(certCfg.alias, cfg.keyPass.toCharArray).asInstanceOf[PrivateKey]
      val keypair = new KeyPair(e.getPublicKey(), key)
      CertificateHolder.create(keyPair = keypair, chain = chain.toList).get
    }
  }

  private[this] def serverKeystore(ks: KeyStore): Try[ServerKeyStore] = Try {

    val certs: Map[String, CertificateHolder] = cfg.certConfigs.map { certCfg =>
      (certCfg.alias, extractServerCertificate(ks, certCfg).get)
    }.filter(_._2.isDefined).toMap.mapValues(_.get)

    ServerKeyStore(ks, certs)
  }
}
