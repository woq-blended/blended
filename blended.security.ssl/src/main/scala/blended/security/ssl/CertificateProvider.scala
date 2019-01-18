package blended.security.ssl

import scala.util.Try

trait CertificateProvider {

  def refreshCertificate(existing: Option[CertificateHolder], cnProvider: CommonNameProvider): Try[CertificateHolder]
}
