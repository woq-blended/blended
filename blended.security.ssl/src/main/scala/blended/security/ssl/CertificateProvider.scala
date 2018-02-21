package blended.security.ssl

import java.security.KeyPair
import java.security.cert.Certificate
import java.security.cert.X509Certificate

import scala.util.Try

case class ServerCertificate(
  keyPair : KeyPair,
  chain : Array[Certificate]
)

trait CertificateProvider {

  def refreshCertificate(existing: Option[X509Certificate]) : Try[ServerCertificate]
}
