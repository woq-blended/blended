package blended.security.cert.internal

import com.typesafe.config.Config

object CertControllerConfig {

  val aliasPath = "alias"
  val keyStorePath = "keyStore"
  val storePassPath = "storePass"
  val keyPassPath = "keyPass"
  val overwritePath = "overwriteForFailure"

  def fromConfig(cfg: Config, hasher: PasswordHasher) = {
    val alias = if (cfg.hasPath(aliasPath)) cfg.getString(aliasPath) else "default"
    val keyStore = if (cfg.hasPath(keyStorePath)) cfg.getString(keyStorePath) else System.getProperty("javax.net.ssl.keyStore")
    val storePass = if (cfg.hasPath(storePassPath)) cfg.getString(storePassPath) else System.getProperty("javax.net.ssl.keyStorePassword")
    val keyPass = if (cfg.hasPath(keyPassPath)) cfg.getString(keyPassPath) else System.getProperty("javax.net.ssl.keyPassword")
    val overwriteForFailure = cfg.hasPath(overwritePath) && cfg.getBoolean(overwritePath)

    CertControllerConfig(
      alias = alias,
      keyStore = keyStore,
      storePass = hasher.password(storePass),
      keyPass = hasher.password(keyPass),
      overwriteForFailure
    )
  }
}

case class CertControllerConfig(
  alias: String,
  keyStore: String,
  storePass: Array[Char],
  keyPass: Array[Char],
  overwriteForFailure: Boolean
)
