package blended.security.ssl.internal

import java.io.{ File, FileInputStream, FileOutputStream }
import java.security.{ KeyPair, KeyStore, PrivateKey }
import java.security.cert.X509Certificate

import blended.security.ssl.{CertificateProvider, ServerCertificate, X509CertificateInfo}
import org.log4s._

import scala.util.{ Success, Try }
import scala.concurrent.duration._
import scala.util.Failure

class CertificateController(cfg: CertControllerConfig, provider: CertificateProvider) {

  private[this] val log = getLogger
  private[this] lazy val keyStore = initKeyStore().get

  private[this] val millisPerDay: Long = 1.day.toMillis

  def checkCertificate(): Try[ServerKeyStore] = {
    log.info(s"Checking Server Certificate for key store [${cfg.keyStore}]")
    checkAndUpdateCertificate(keyStore)
  }

  def serverKeyStore(): KeyStore = keyStore

  //  def extractCertInfo(ks: KeyStore): Try[X509CertificateInfo] = Try {
  //    Option(ks.getCertificate(cfg.alias)) match {
  //      case Some(cert: X509Certificate) => X509CertificateInfo(cert)
  //      case Some(cert) => sys.error(s"The certificate with alias [${cfg.alias}] is not a X509Certificate")
  //      case None => sys.error(s"No certificate found with alias [${cfg.alias}] in key store [${ks}]")
  //    }
  //  }

  private[this] def extractServerCertificate(ks: KeyStore): Try[Option[ServerCertificate]] = Try {
    Option(ks.getCertificateChain(cfg.alias)).map { chain =>
      val e = ks.getCertificate(cfg.alias)
      val key = ks.getKey(cfg.alias, cfg.keyPass).asInstanceOf[PrivateKey]
      val keypair = new KeyPair(e.getPublicKey(), key)
      ServerCertificate.create(keyPair = keypair, chain = chain.toList).get
    }
  }

  private[this] def checkAndUpdateCertificate(ks: KeyStore): Try[ServerKeyStore] = {

    val existingCert = extractServerCertificate(ks).recoverWith {
      case e if cfg.overwriteForFailure => Success(None)
    }.get

    existingCert match {
      case Some(serverCertificate) =>
        val certInfo = X509CertificateInfo(serverCertificate.chain.head)

        val remaining = certInfo.notAfter.getTime() - System.currentTimeMillis()

        if (remaining <= cfg.minValidDays * millisPerDay) {
          log.info(s"Certificate [${cfg.alias}] is about to expire in [${remaining.toDouble / millisPerDay}] days...refreshing certificate.")
          // try to obtain new certificate
          // if unable to refresh continue using old certicate if remaining is still positive
          // if negative fail
          //        throw new Exception("not implemented")
          updateKeystore(ks, existingCert).recoverWith {
            case e: Throwable =>
              log.debug(e)("Could not refresh the keystore, returning the old one")
              Success(ServerKeyStore(ks, serverCertificate))
          }
        } else {
          log.info(s"Server certificate [${cfg.alias}] is still vaild.")
          Success(ServerKeyStore(ks, serverCertificate))
        }
      case None =>
        log.info(s"Certificate with alias [${cfg.alias}] does not yet exist")
        updateKeystore(ks, existingCert)
    }

  }

  private[this] def updateKeystore(ks: KeyStore, existingCert: Option[ServerCertificate]): Try[ServerKeyStore] = Try {
    log.info("Aquiring new certificate from certificate provider ...")

    //    val existing = extractServerCertificate(ks).recoverWith {
    //      case e if cfg.overwriteForFailure => Success(None)
    //    }.get

    val newCert = provider.refreshCertificate(existingCert)

    newCert match {
      case Failure(e) =>
        log.error(e)("Could not update keystore")
        throw e
      case Success(cert) =>
        log.info(s"Successfully obtained certificate from certificate provider [${provider}]")
        ks.setKeyEntry(cfg.alias, cert.keyPair.getPrivate(), cfg.keyPass, cert.chain.toArray)

        val fos = new FileOutputStream(cfg.keyStore)
        try {
          ks.store(fos, cfg.storePass)
          log.info(s"Successfully written modified key store to [${cfg.keyStore}]")
        } finally {
          fos.close()
        }
        ServerKeyStore(ks, cert)
    }
  }

  private[this] def initKeyStore(): Try[KeyStore] = {

    log.debug("Initializing key store for server certificate ...")

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
    }

    Success(ks)
  }
}
