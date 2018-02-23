package blended.security.ssl.internal

import com.typesafe.config.Config
import blended.util.config.Implicits._

/**
 * Configuration of [[CertController]]
 *
 * @param alias The alias of the certificate.
 * @param keyStore The used keyStore.
 * @param storePass The password used to open the key store.
 * @param keyPass The key password.
 * @param minValidDays
 *   If the days until the end of the certificate validity fall below this threshold,
 *   the [[CertController]] will try to re-new the certificate.
 */
case class CertControllerConfig(
  alias: String,
  keyStore: String,
  storePass: Array[Char],
  keyPass: Array[Char],
  minValidDays: Int,
  overwriteForFailure: Boolean)

object CertControllerConfig {

  /**
   * Read a [[CertControllerConfig]] from a typesafe [[Config]],
   * using the given [[PasswordHasher]] to hash the passwords (`keyPass` and `storePass`).
   */
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


