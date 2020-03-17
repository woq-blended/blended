package blended.security.ssl

sealed trait CertificateChange {
  def changed: Boolean
}

object CertificateChange {
  sealed abstract class CertificateChangeBase protected (val changed: Boolean) extends CertificateChange
  case object Added extends CertificateChangeBase(true)
  case object Updated extends CertificateChangeBase(true)
  case object Unchanged extends CertificateChangeBase(false)
}

