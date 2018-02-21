package blended.security.cert.internal

import com.typesafe.config.Config
import blended.util.config.Implicits._

case class SelfSignedConfig(
  subject: String,
  keyStrength: Int,
  sigAlg: String,
  validDays: Int)

object SelfSignedConfig {

  val sigAlgPath = "signatureAlgorithm"
  val validDaysPath = "validDays"

  def fromConfig(cfg: Config) = {
    val subject = cfg.getString("subject")
    val keyStrength = cfg.getInt("keyStrength", 2048)
    val signatureAlgorithm = cfg.getString("signatureAlgorithm", "SHA256withRSA")
    val validDays = cfg.getInt("validDays", 1)

    SelfSignedConfig(subject, keyStrength, signatureAlgorithm, validDays)
  }
}

