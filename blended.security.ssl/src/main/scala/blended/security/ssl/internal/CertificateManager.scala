package blended.security.ssl.internal

import java.io.{File, FileInputStream, FileOutputStream}
import java.security.{KeyPair, KeyStore, PrivateKey}
import java.util.Date

import blended.security.ssl.{CertificateProvider, ServerCertificate, X509CertificateInfo}
import domino.capsule._
import domino.service_providing.ServiceProviding
import javax.net.ssl.SSLContext
import org.osgi.framework.BundleContext

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

/**
  * A class to manage one or more server side certificates within a given keystore
  * to be used as SSL server certificates.
  */
class CertificateManager(
  override val bundleContext: BundleContext,
  override val capsuleContext: CapsuleContext,
  cfg: CertificateManagerConfig,
  providerMap: Map[String, CertificateProvider]
) extends Capsule with CapsuleConvenience with ServiceProviding {

  private[this] val log = org.log4s.getLogger
  private[this] val millisPerDay = 1.day.toMillis

  private[this] lazy val keyStore = loadKeyStore()

  def getKeystore() : ServerKeyStore = keyStore.get

  private[internal] def registerSslContextProvider(ks: KeyStore): CapsuleScope = capsuleContext.executeWithinNewCapsuleScope {
    log.debug("Registering SslContextProvider type=client and type=server")
    val sslCtxtProvider = new SslContextProvider(ks, cfg.keyPass)
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

  def start() : Unit = {

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
  }

  override def stop(): Unit = {}

  def nextCertificateTimeout() : Date = getKeystore().serverCertificates.values.map(_.chain.head.getNotAfter).min

  private[this] def loadKeyStore(): Try[ServerKeyStore] = {

    log.info(s"Initializing key store [${cfg.keyStore}] for server certificate(s) ...")

    val ks = KeyStore.getInstance("PKCS12")
    val f = new File(cfg.keyStore)

    if (f.exists()) {
      val fis = new FileInputStream(f)
      try {
        ks.load(fis, cfg.storePass)
      } finally {
        fis.close()
      }
    } else {
      log.info(s"Creating empty key store  ...")
      ks.load(null, cfg.storePass)
      saveKeyStore(ks)
    }

    serverKeystore(ks)
  }

  def checkCertificates() : Try[(ServerKeyStore, List[String])] = Try { (keyStore.get, List.empty) }

  private[this] def checkAndUpdateCertificate(ks: KeyStore, certCfg: CertificateConfig): Try[ServerKeyStore] = Try {

    serverKeystore(ks).get
//    val existingCert = extractServerCertificate(ks, certCfg).get
//
//    existingCert match {
//      case Some(serverCertificate) =>
//        val certInfo = X509CertificateInfo(serverCertificate.chain.head)
//
//        val remaining = certInfo.notAfter.getTime() - System.currentTimeMillis()
//
//        if (remaining <= certCfg.minValidDays * millisPerDay) {
//          log.info(s"Certificate [${certCfg.alias}] is about to expire in [${remaining.toDouble / millisPerDay}] days...refreshing certificate.")
//          updateKeystore(ks, existingCert).recoverWith {
//            case e: Throwable =>
//              log.debug(e)("Could not refresh the keystore, returning the old one")
//              Success(ServerKeyStore(ks, serverCertificate))
//          }
//        } else {
//          log.info(s"Server certificate [${cfg.alias}] is still vaild.")
//          Success(ServerKeyStore(ks, serverCertificate))
//        }
//      case None =>
//        log.info(s"Certificate with alias [${certCfg.alias}] does not yet exist")
//        updateKeystore(ks, existingCert)
//    }
  }

  private[this] def updateKeystore(ks: KeyStore, existingCert: Option[ServerCertificate], certCfg: CertificateConfig): Try[ServerKeyStore] = Try {
    log.info(s"Aquiring new certificate from certificate provider [${certCfg.provider}]")

    val provider = providerMap.get(certCfg.provider).get
    val newCert = provider.refreshCertificate(existingCert)

    newCert match {
      case Failure(e) =>
        log.error(e)("Could not update keystore")
        throw e
      case Success(cert) =>
        val info = X509CertificateInfo(cert.chain.head)
        log.info(s"Successfully obtained certificate from certificate provider [$provider] : $info")
        ks.setKeyEntry(certCfg.alias, cert.keyPair.getPrivate(), cfg.keyPass, cert.chain.toArray)
        saveKeyStore(ks).get
        serverKeystore(ks).get
    }
  }

  private[this] def saveKeyStore(ks: KeyStore) : Try[KeyStore] = Try {
    val fos = new FileOutputStream(cfg.keyStore)
    try {
      ks.store(fos, cfg.storePass)
      log.info(s"Successfully written modified key store to [${cfg.keyStore}]")
    } finally {
      fos.close()
    }

    ks
  }

  // Extract a single server certificate from the underlying keystore
  private[this] def extractServerCertificate(ks: KeyStore, certCfg: CertificateConfig): Try[Option[ServerCertificate]] = Try {
    Option(ks.getCertificateChain(certCfg.alias)).map { chain =>
      val e = ks.getCertificate(certCfg.alias)
      val key = ks.getKey(certCfg.alias, cfg.keyPass).asInstanceOf[PrivateKey]
      val keypair = new KeyPair(e.getPublicKey(), key)
      ServerCertificate.create(keyPair = keypair, chain = chain.toList).get
    }
  }

  private[this] def serverKeystore(ks: KeyStore) : Try[ServerKeyStore] = Try {

    val certs : Map[String, ServerCertificate] = cfg.certConfigs.map { certCfg =>
      (certCfg.alias, extractServerCertificate(ks, certCfg).get)
    }.filter(_._2.isDefined).toMap.mapValues(_.get)

    ServerKeyStore(ks, certs)
  }
}
