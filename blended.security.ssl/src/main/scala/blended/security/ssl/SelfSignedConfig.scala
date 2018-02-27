package blended.security.ssl

import blended.util.config.Implicits._
import com.typesafe.config.Config

case class SelfSignedConfig(
  commonNameProvider: CommonNameProvider,
  keyStrength: Int,
  sigAlg: String,
  validDays: Int)

object SelfSignedConfig {

  val sigAlgPath = "signatureAlgorithm"
  val validDaysPath = "validDays"

  def fromConfig(commonNameProvider: CommonNameProvider, cfg: Config) = {
    val keyStrength = cfg.getInt("keyStrength", 2048)
    val signatureAlgorithm = cfg.getString("signatureAlgorithm", "SHA256withRSA")
    val validDays = cfg.getInt("validDays", 1)


    SelfSignedConfig(commonNameProvider, keyStrength, signatureAlgorithm, validDays)
  }
}

