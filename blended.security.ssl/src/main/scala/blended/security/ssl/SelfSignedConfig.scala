package blended.security.ssl

import blended.container.context.api.ContainerIdentifierService
import blended.security.ssl.internal.ConfigCommonNameProvider
import blended.util.config.Implicits._
import com.typesafe.config.Config

case class SelfSignedConfig(
  commonNameProvider: CommonNameProvider,
  keyStrength: Int,
  sigAlg: String,
  validDays: Int
)

object SelfSignedConfig {

  val sigAlgPath = "signatureAlgorithm"
  val validDaysPath = "validDays"

  def fromConfig(cfg: Config, idSvc: ContainerIdentifierService) = {
    val keyStrength = cfg.getInt("keyStrength", 2048)
    val signatureAlgorithm = cfg.getString("signatureAlgorithm", "SHA256withRSA")
    val validDays = cfg.getInt("validDays", 1)


    SelfSignedConfig(new ConfigCommonNameProvider(cfg, idSvc), keyStrength, signatureAlgorithm, validDays)
  }
}

