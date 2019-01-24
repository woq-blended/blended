package blended.security.ssl.internal

import blended.security.ssl.CertificateHolder

import scala.util.Try

class InconsistentKeystoreException(m : String) extends Exception(m)

case class MemoryKeystore(certificates: Map[String, CertificateHolder]) {

  // The in memory keystore is consistent if and only if all certificates have a private key defined
  // or none of it does have a private key defined.
  def consistent : Boolean = {
    certificates.values.forall(_.privateKey.isDefined) || certificates.values.forall(_.privateKey.isEmpty)
  }

  def update(alias: String, cert : CertificateHolder) : Try[MemoryKeystore] = Try {
    val result : MemoryKeystore = MemoryKeystore(certificates.filterKeys(_ != alias) + (alias -> cert))

    if (result.consistent) {
      result
    } else {
      throw new InconsistentKeystoreException("Keystore must be consistent after update")
    }
  }
}
