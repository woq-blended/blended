package blended.security.cert.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.security.cert.X509Certificate

/**
 * Information about a X509 certificate.
 */
case class X509CertificateInfo(
    cn: String,
    issuer: String,
    notBefore: Date,
    notAfter: Date,
    serial: BigInt,
    sigAlg: String) {

  override def toString: String = getClass().getSimpleName() +
    "(cn=" + cn +
    ",issuer=" + issuer +
    ",notBefore=" + X509CertificateInfo.simpleDateFormat.format(notBefore) +
    ",notAfter=" + X509CertificateInfo.simpleDateFormat.format(notAfter) +
    ",serial=" + serial +
    ",sigAlg=" + sigAlg +
    ")"
}

case object X509CertificateInfo {

  val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS")

  def apply(cert: X509Certificate): X509CertificateInfo = {
    X509CertificateInfo(
      cn = cert.getSubjectDN().toString(),
      issuer = cert.getIssuerDN().toString(),
      notBefore = cert.getNotBefore(),
      notAfter = cert.getNotAfter(),
      serial = cert.getSerialNumber(),
      sigAlg = cert.getSigAlgName()
    )
  }
}

