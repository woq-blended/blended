package blended.sslcontext.internal

import java.security.KeyStore
import java.security.cert.X509Certificate

import blended.sslcontext.CertificateProvider

case class CertificateManagerConfig(
  certificatePath : String,
  password : String,
  provider : CertificateProvider
) {
}

class CertificateManager(conf: CertificateManagerConfig) {

  private[this] def keystore() : KeyStore = {
    KeyStore.getInstance(KeyStore.getDefaultType())
  }

  def getCurrentCertificate() : Option[X509Certificate] = {

    None
  }

}
