package blended.security.ssl

import scala.util.Try

/**
  * A certificate provider will retrieve a new Certificate wrapped in a CertificateHolder.
  * If an existing certificate is given, the provider shall update the certificate (i.e. by
  * extending the validity range and obtaining a new signature. If no existing certificate is
  * given, an initial Certificate shall be obtained.
  *
  * The CommonNameProvider is responsible for providing the common name of the certificate and also
  * a list of subject alternative names, if any need to be set.
  */
trait CertificateProvider {

  def refreshCertificate(existing: Option[CertificateHolder], cnProvider: CommonNameProvider): Try[CertificateHolder]
}
