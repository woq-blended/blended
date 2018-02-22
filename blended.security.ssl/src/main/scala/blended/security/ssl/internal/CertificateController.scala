package blended.security.ssl.internal

import java.io.{File, FileInputStream, FileOutputStream}
import java.security.{KeyPair, KeyStore, PrivateKey}
import java.security.cert.X509Certificate

import blended.security.ssl.{CertificateProvider, ServerCertificate}
import org.log4s._

import scala.util.{Success, Try}
import scala.concurrent.duration._
import scala.util.Failure

class CertificateController(cfg: CertControllerConfig, provider: CertificateProvider) {

  private[this] val log = getLogger
  private[this] lazy val keyStore = initKeyStore().get

  private[this] val millisPerDay: Long = 1.day.toMillis

  def checkCertificate(): Try[KeyStore] = {
    log.info(s"Checking Server Certificate for key store [${cfg.keyStore}]")
    checkAndUpdateCertificate(keyStore)
  }

  def serverKeyStore(): KeyStore = keyStore

  private[this] def checkAndUpdateCertificate(ks: KeyStore): Try[KeyStore] = {

    if (ks.containsAlias(cfg.alias)) {
      log.info(s"Checking existing certificate with alias [${cfg.alias}]")
      val certInfo = X509CertificateInfo(ks.getCertificate(cfg.alias).asInstanceOf[X509Certificate])

      val remaining = certInfo.notAfter.getTime() - System.currentTimeMillis()

      if (remaining <= cfg.minValidDays * millisPerDay) {
        log.info(s"Certificate [${cfg.alias}] is about to expire in [${remaining.toDouble / millisPerDay}] days...refreshing certificate.")
        // try to obtain new certificate
        // if unable to refresh continue using old certicate if remaining is still positive
        // if negative fail
        //        throw new Exception("not implemented")
        updateKeystore(ks).recoverWith {
          case e: Throwable =>
            log.debug(e)("Could not refresh the keystore, returning the old one")
            Success(ks)
        }
      } else {
        log.info(s"Server certificate [${cfg.alias}] is still vaild.")
        Success(ks)
      }
    } else {
      log.info(s"Certificate with alias [${cfg.alias}] does not yet exist")
      updateKeystore(ks)
    }
  }

  private[this] def updateKeystore(ks: KeyStore): Try[KeyStore] = {
    log.info("Aquiring new certificate from certificate provider ...")

    val existing = Option(ks.getCertificate(cfg.alias).asInstanceOf[X509Certificate]).map{ e =>

      val key = ks.getKey(cfg.alias, cfg.keyPass).asInstanceOf[PrivateKey]
      val keypair = new KeyPair(e.getPublicKey(), key)
      ServerCertificate(keyPair = keypair, chain = List(e))
    }

    val newCert = provider.refreshCertificate(existing)

    newCert match {
      case Failure(e) =>
        log.error(e)("Could not update keystore")
        Failure(e)
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
        Success(ks)
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
