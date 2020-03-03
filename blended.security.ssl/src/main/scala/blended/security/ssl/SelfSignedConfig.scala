package blended.security.ssl

import blended.container.context.api.ContainerContext
import blended.security.ssl.internal.ConfigCommonNameProvider
import blended.util.config.Implicits._
import com.typesafe.config.Config

case class SelfSignedConfig(
  commonNameProvider : CommonNameProvider,
  keyStrength : Int,
  sigAlg : String,
  validDays : Int
)

object SelfSignedConfig {

  private val defaultKeyStrength : Int = 2048

  val sigAlgPath = "signatureAlgorithm"
  val validDaysPath = "validDays"

  def fromConfig(cfg : Config, ctCtxt : ContainerContext) : SelfSignedConfig = {
    val keyStrength = cfg.getInt("keyStrength", defaultKeyStrength)
    val signatureAlgorithm = cfg.getString("signatureAlgorithm", "SHA256withRSA")
    val validDays = cfg.getInt("validDays", 1)

    SelfSignedConfig(new ConfigCommonNameProvider(cfg, ctCtxt), keyStrength, signatureAlgorithm, validDays)
  }
}

