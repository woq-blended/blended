package blended.security.cert

import java.security.KeyPair
import java.security.cert.X509Certificate

case class ServerCertificate(
  keyPair : KeyPair,
  certificate : X509Certificate
)

trait CertificateProvider {

  def serverCertificate() : ServerCertificate

}
