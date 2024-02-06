package blended.security.ssl

import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date

import scala.jdk.CollectionConverters._

/**
 * Information about a X509 certificate.
 */
case class X509CertificateInfo(
  cn : String,
  altNames : List[String],
  issuer : String,
  notBefore : Date,
  notAfter : Date,
  serial : BigInt,
  sigAlg : String
) {

  override def toString : String = getClass().getSimpleName() +
    "(cn=" + cn +
    ",altNames=" + altNames.mkString("[", ",", "]") +
    ",issuer=" + issuer +
    ",notBefore=" + X509CertificateInfo.simpleDateFormat.format(notBefore) +
    ",notAfter=" + X509CertificateInfo.simpleDateFormat.format(notAfter) +
    ",serial=" + serial +
    ",sigAlg=" + sigAlg +
    ")"
}

case object X509CertificateInfo {

  val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS")

  def apply(cert : X509Certificate) : X509CertificateInfo = {

    val altNames = Option(cert.getSubjectAlternativeNames()) match {
      case Some(names) => names.asScala.map { n =>
        n.asScala.mkString("(", ",", ")")
      }.toList
      case None => List.empty[String]
    }

    X509CertificateInfo(
      cn = cert.getSubjectX500Principal().toString(),
      altNames = altNames,
      issuer = cert.getIssuerX500Principal().toString(),
      notBefore = cert.getNotBefore(),
      notAfter = cert.getNotAfter(),
      serial = cert.getSerialNumber(),
      sigAlg = cert.getSigAlgName()
    )
  }
}

