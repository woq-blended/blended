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
  alias : String,
  cert : CertificateHolder,
  results : Seq[(ResultLevel.ResultLevel, String)]
) {

  val infoOnly : Boolean = results.map(_._1).distinct.forall(_ == ResultLevel.INFO)
}

trait CertificateChecker {

  /**
    * Check a single certificate and determine the result messages for the validity check of that
    * certificate.
    */
  def checkCertificate(alias : String, cert: CertificateHolder) : Option[CertificateCheckResult]

  /**
    * Check the certificates of a given [[MemoryKeystore]].
    * @param certs
    * @return A sequence of [[CertificateCheckResult]] with result messages for all certificates that
    *         produce "remarks". The certificates are a subset of the certificates in the checked
    *         keystore.
    */
  def checkCertificates(certs : MemoryKeystore) : Seq[CertificateCheckResult] = {
    certs.certificates.flatMap { case (alias, cert) =>
      checkCertificate(alias, cert)
    }.toSeq
  }
}

class RemainingValidityChecker(minValidDays: Int) extends CertificateChecker {

  private val millisPerDay : Long = 1.day.toMillis

  override def checkCertificate(alias : String, cert: CertificateHolder): Option[CertificateCheckResult] = {

    val certInfo : X509CertificateInfo = X509CertificateInfo(cert.chain.head)
    val remaining : Long = certInfo.notAfter.getTime() - System.currentTimeMillis()

    Some(
      if (remaining <= minValidDays * millisPerDay) {
        val msg = s"Certificate for [${cert.chain.head.getSubjectX500Principal()}] is about to expire in ${remaining.toDouble / millisPerDay} days"
        CertificateCheckResult(alias, cert, Seq( (ResultLevel.WARN, msg) ))
      } else {
        val msg = s"Certificate for [${cert.chain.head.getSubjectX500Principal()}] is still valid"
        CertificateCheckResult(alias, cert, Seq((ResultLevel.INFO, msg)))
      }
    )
  }
}
