package blended.sslcontext

import java.security.KeyPair
import java.security.cert.X509Certificate

trait CertificateProvider {

  def provideCertificate() : (KeyPair, X509Certificate)

}
