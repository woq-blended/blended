package blended.security.ssl.internal

import blended.security.ssl.{CertificateHolder, X509CertificateInfo}
import scala.concurrent.duration._

object ResultLevel extends Enumeration {
  type ResultLevel = Value
  val INFO, WARN, ERROR = Value
}

/**
  * Encapsulate results of a certificate checker
  * @param cert The certificate that has been checked
  * @param results A check message and a level of the message
  */
case class CertificateCheckResult(
  cert : CertificateHolder,
  results : Seq[(ResultLevel.ResultLevel, String)]
)

trait CertificateChecker {

  /**
    * Check the certificates of a given [[MemoryKeystore]].
    * @param certs
    * @return A sequence of [[CertificateCheckResult]] with result messages for all certificates that
    *         produce "remarks". The certificates are a subset of the certificates in the checked
    *         keystore.
    */
  def checkCertificates(certs : MemoryKeystore) : Seq[CertificateCheckResult]
}

class RemainingValidityChecker(minValidDays: Int) extends CertificateChecker {

  private val millisPerDay : Long = 1.day.toMillis

  override def checkCertificates(certs: MemoryKeystore): Seq[CertificateCheckResult] = {

    certs.certificates.values.map { cert =>
      val certInfo : X509CertificateInfo = X509CertificateInfo(cert.chain.head)
      val remaining : Long = certInfo.notAfter.getTime() - System.currentTimeMillis()

      if (remaining <= minValidDays * millisPerDay) {
        val msg = s"Certificate for [${cert.chain.head.getSubjectX500Principal()}] is about to expire in ${remaining.toDouble / millisPerDay} days"
        CertificateCheckResult(cert, Seq( (ResultLevel.WARN, msg) ))
      } else {
        val msg = s"Certificate for [${cert.chain.head.getSubjectX500Principal()}] is still valid"
        CertificateCheckResult(cert, Seq((ResultLevel.INFO, msg)))
      }
    }.toSeq
  }
}
