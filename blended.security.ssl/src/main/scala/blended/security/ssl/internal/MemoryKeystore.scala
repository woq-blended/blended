package blended.security.ssl.internal

import java.util.Date

import blended.security.ssl.{CertificateHolder, CertificateProvider, InconsistentKeystoreException, X509CertificateInfo}
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class MemoryKeystore(certificates: Map[String, CertificateHolder]) {

  private[this] val log : Logger = Logger[MemoryKeystore]
  private[this] val millisPerDay : Long = 1.day.toMillis

  // The in memory keystore is consistent if and only if all certificates have a private key defined
  // or none of it does have a private key defined.
  def consistent : Boolean = {
    certificates.values.forall(_.privateKey.isDefined) || certificates.values.forall(_.privateKey.isEmpty)
  }

  def update(alias: String, cert : CertificateHolder) : Try[MemoryKeystore] = Try {
    val result : MemoryKeystore = MemoryKeystore(certificates.filterKeys(_ != alias) + (alias -> cert))

    if (result.consistent) {
      result
    } else {
      throw new InconsistentKeystoreException("Keystore must be consistent after update")
    }
  }

  def nextCertificateTimeout(): Try[Date] = Try {
    certificates match {
      case e if e.isEmpty => new Date()
      case m => m.values.map(_.chain.head.getNotAfter).min
    }
  }

  private[this] def refreshCertificate(
    certCfg: CertificateConfig,
    providerMap: Map[String, CertificateProvider],
    oldCert : Option[CertificateHolder]
  ) : Try[(MemoryKeystore, List[String])] = Try {
    providerMap.get(certCfg.provider) match {
      case None =>
        log.warn(s"Certificate provider [${certCfg.provider}] not found, not updating certificate [${certCfg.alias}]")
        (this, List.empty)
      case Some(p) =>
        (update(certCfg.alias, p.refreshCertificate(oldCert, certCfg.cnProvider).get).get, List(certCfg.alias))
    }
  }

  private[this] def changed(
    current : MemoryKeystore,
    certConfigs: List[CertificateConfig],
    providerMap: Map[String, CertificateProvider],
    changedAliases: List[String]
  ): Try[(MemoryKeystore, List[String])] = Try {

    certConfigs match {
      // No further certificate configs to check, returning result
      case Nil =>
        (current, changedAliases)

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
                case Success((newMs, c)) =>
                  changed(newMs, tail, providerMap, c ::: changedAliases).get

                case Failure(t) =>
                  log.info(s"Could not refresh certificate [${head.alias}], reusing the existing one.")
                  changed(current, tail, providerMap, changedAliases).get
              }
            } else {
              log.info(s"Server certificate [${head.alias}] is still valid.")
              changed(current, tail, providerMap, changedAliases).get
            }

          // The keystore does not yet have a certificate for that alias
          case None =>
            log.info(s"Certificate with alias [${head.alias}] does not yet exist.")
            refreshCertificate(head, providerMap, None) match {
              case Success((newMs, c)) =>
                changed(newMs, tail, providerMap, c ::: changedAliases).get

              case Failure(t) =>
                log.info(s"Could not refresh certificate [${head.alias}], reusing the existing one.")
                changed(current, tail, providerMap, changedAliases).get
            }
        }
    }
  }

  def refreshCertificates(
    certCfgs : List[CertificateConfig],
    providerMap : Map[String, CertificateProvider]
  ): Try[(MemoryKeystore, List[String])] = {

    changed(this, certCfgs, providerMap, List.empty)
  }

}
