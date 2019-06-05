package blended.security.ssl

import java.util.Date

import blended.security.ssl.internal.CertificateConfig
import blended.util.logging.Logger
import javax.security.auth.x500.X500Principal

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class MemoryKeystore(certificates : Map[String, CertificateHolder]) {

  private[this] val log : Logger = Logger[MemoryKeystore]
  private[this] val millisPerDay : Long = 1.day.toMillis

  val changedAliases : List[String] = certificates.filter { case (_, v) => v.changed }.keys.toList

  // The in memory keystore is consistent if and only if all certificates have a private key defined
  // or none of it does have a private key defined.
  def consistent : Boolean = {
    certificates.values.forall(_.privateKey.isDefined) || certificates.values.forall(_.privateKey.isEmpty)
  }

  def update(alias : String, cert : CertificateHolder) : Try[MemoryKeystore] = Try {

    log.info(s"Updating memory keystore [alias=$alias]")
    val result : MemoryKeystore =
      MemoryKeystore(certificates.filterKeys(_ != alias) + (alias -> cert.copy(changed = true)))

    if (result.consistent) {
      log.info(s"Updated keystore aliases : [${result.certificates.keys}]")
      result
    } else {
      throw new InconsistentKeystoreException("Keystore must be consistent after update")
    }
  }

  def findByPrincipal(principal : X500Principal) : Option[CertificateHolder] = {
    certificates.values.find { ch =>
      ch.subjectPrincipal.isDefined &&
        ch.subjectPrincipal.forall(_.equals(principal))
    }
  }

  def nextCertificateTimeout() : Try[Date] = Try {
    certificates match {
      case e if e.isEmpty => new Date()
      case m              => m.values.map(_.chain.head.getNotAfter).min
    }
  }

  private[this] def refreshCertificate(
    certCfg : CertificateConfig,
    providerMap : Map[String, CertificateProvider],
    oldCert : Option[CertificateHolder]
  ) : Try[MemoryKeystore] = Try {
    providerMap.get(certCfg.provider) match {
      case None =>
        log.warn(s"Certificate provider [${certCfg.provider}] not found, not updating certificate [${certCfg.alias}]")
        this
      case Some(p) =>
        val newCert = p.refreshCertificate(oldCert, certCfg.cnProvider).get
        log.info(s"Obtained certificate for alias [${certCfg.alias}] : [$newCert]")
        update(certCfg.alias, newCert).get
    }
  }

  private[ssl] def changed(
    certConfigs : List[CertificateConfig],
    providerMap : Map[String, CertificateProvider]
  ) : Try[MemoryKeystore] = Try {

    certConfigs match {
      // No further certificate configs to check, returning result
      case Nil => this

      // Otherwise we check the certificate specifies by the cert config at the head of
      // the list and update the result
      case head :: tail =>
        certificates.get(head.alias) match {
          // The keystore already has a certificate with the requested alias
          case Some(serverCertificate) =>
            val certInfo = X509CertificateInfo(serverCertificate.chain.head)
            val remaining = certInfo.notAfter.getTime() - System.currentTimeMillis()

            if (remaining <= head.minValidDays * millisPerDay) {
              log.info(s"Certificate [${head.alias}] is about to expire in ${remaining.toDouble / millisPerDay} days...refreshing certificate")

              refreshCertificate(head, providerMap, Some(serverCertificate)) match {
                case Success(newMs) =>
                  newMs.changed(tail, providerMap).get

                case Failure(t) =>
                  log.info(t)(s"Could not refresh certificate [${head.alias}], reusing the existing one.")
                  changed(tail, providerMap).get
              }
            } else {
              log.info(s"Server certificate [${head.alias}] is still valid.")
              changed(tail, providerMap).get
            }

          // The keystore does not yet have a certificate for that alias
          case None =>
            log.info(s"Certificate with alias [${head.alias}] does not yet exist.")
            refreshCertificate(head, providerMap, None) match {
              case Success(newMs) =>
                newMs.changed(tail, providerMap).get

              case Failure(t) =>
                log.error(t)(s"Could not initially create certificate [${head.alias}] : [${t.getMessage()}].")
                // changed(tail, providerMap).get
                throw new InitialCertificateProvisionException(s"Could not initially create certificate [${head.alias}] : [${t.getMessage()}].")
            }
        }
    }
  }

  def refreshCertificates(
    certCfgs : List[CertificateConfig],
    providerMap : Map[String, CertificateProvider]
  ) : Try[MemoryKeystore] = {

    val result = changed(certCfgs, providerMap)
    // detect failure
    result
  }

}
