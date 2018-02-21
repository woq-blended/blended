package blended.security.cert.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.security.cert.X509Certificate

case object X509CertificateInfo {

  protected val sdf = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS")

  def apply(cert: X509Certificate) : X509CertificateInfo = {

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

case class X509CertificateInfo(
  cn: String,
  issuer: String,
  notBefore : Date,
  notAfter: Date,
  serial: BigInt,
  sigAlg : String
) {

  import X509CertificateInfo.sdf

  override def toString: String = {
    s"${getClass().getSimpleName()}(cn=$cn, issuer=$issuer, notBefore=${sdf.format(notBefore)}, " +
    s"notAfter=${sdf.format(notAfter)}, serial=$serial, sigAlg=$sigAlg)"
  }
}
