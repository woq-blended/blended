package blended.security.ssl.internal

import com.typesafe.config.Config
import blended.util.config.Implicits._

object CertControllerConfig {

  def fromConfig(cfg: Config, hasher: PasswordHasher) = {
    val alias = cfg.getString("alias", "default")
    val keyStore = cfg.getString("keyStore", System.getProperty("javax.net.ssl.keyStore"))
    val storePass = cfg.getString("storePass", System.getProperty("javax.net.ssl.keyStorePassword"))
    val keyPass = cfg.getString("keyPass", System.getProperty("javax.net.ssl.keyPassword"))
    val overwriteForFailure = cfg.getBoolean("overwriteForFailure", false)
    val minValidDays = cfg.getInt("minValidDays", 10)

    CertControllerConfig(
      alias = alias,
      keyStore = keyStore,
      storePass = hasher.password(storePass),
      keyPass = hasher.password(keyPass),
      minValidDays = minValidDays,
      overwriteForFailure
    )
  }
}

case class CertControllerConfig(
  alias: String,
  keyStore: String,
  storePass: Array[Char],
  keyPass: Array[Char],
  minValidDays : Int,
  overwriteForFailure: Boolean
)
