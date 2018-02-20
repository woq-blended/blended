package blended.security.cert.internal

import com.typesafe.config.Config

object SelfSignedConfig{

  val keyStrengthPath = "keyStrength"
  val sigAlgPath = "signatureAlgorithm"
  val subjectPath = "subject"
  val validDaysPath = "validDays"

  def fromConfig(cfg : Config) = {

    val subject = cfg.getString(subjectPath)
    val keyStrength = if (cfg.hasPath(keyStrengthPath)) cfg.getInt(keyStrengthPath) else 2048
    val signatureAlgorithm = if (cfg.hasPath(sigAlgPath)) cfg.getString(sigAlgPath) else "SHA256withRSA"
    val validDays = if (cfg.hasPath(validDaysPath)) cfg.getInt(validDaysPath) else 1

    SelfSignedConfig(
      subject, keyStrength, signatureAlgorithm, validDays
    )
  }
}

case class SelfSignedConfig(

  subject : String,
  keyStrength : Int,
  sigAlg : String,
  validDays : Int
)

