package blended.security.cert

import java.security.KeyPair
import java.security.cert.{Certificate, X509Certificate}

case class ServerCertificate(
  keyPair : KeyPair,
  chain : Array[Certificate]
)

trait CertificateProvider {

  def refreshCertificate(existing: Option[X509Certificate]) : ServerCertificate
}
