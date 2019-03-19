package blended.security.ssl

import scala.util.Try

trait CertificateManager {
  
  /**
   * @return When successful, a tuple of keystore and a list of updated certificate aliases, else the failure.
   */
  def checkCertificates(): Try[Option[MemoryKeystore]]

}
