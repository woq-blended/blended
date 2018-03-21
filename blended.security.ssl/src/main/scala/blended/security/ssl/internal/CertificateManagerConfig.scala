package blended.security.ssl.internal

import com.typesafe.config.Config
import blended.util.config.Implicits._

import scala.util.Try

/**
 * Configuration of [[CertificateManager]]
 *
 * @param keyStore The used keyStore.
 * @param storePass The password used to open the key store.
 * @param keyPass The key password.
 *   If the days until the end of the certificate validity fall below this threshold,
 *   the [[CertificateManager]] will try to re-new the certificate.
 */
case class CertificateManagerConfig(
  keyStore: String,
  storePass: Array[Char],
  keyPass: Array[Char],
  certConfigs: List[CertificateConfig],
  refresherConfig: Option[RefresherConfig]
)

object CertificateManagerConfig {

  /**
   * Read a [[CertificateManagerConfig]] from a typesafe [[Config]],
   * using the given [[PasswordHasher]] to hash the passwords (`keyPass` and `storePass`).
   */
  def fromConfig(cfg: Config, hasher: PasswordHasher) = {
    val keyStore = cfg.getString("keyStore", System.getProperty("javax.net.ssl.keyStore"))
    val storePass = cfg.getString("storePass", System.getProperty("javax.net.ssl.keyStorePassword"))
    val keyPass = cfg.getString("keyPass", System.getProperty("javax.net.ssl.keyPassword"))

    val certConfigs = cfg.getConfigMap("certificates", Map.empty).map { case (k, v) =>
      CertificateConfig.fromConfig(k, v)
    }.toList

    val refresherConfig = cfg.getConfigOption("refresher").map(c => RefresherConfig.fromConfig(c).get)

    CertificateManagerConfig(
      keyStore = keyStore,
      storePass = hasher.password(storePass),
      keyPass = hasher.password(keyPass),
      certConfigs,
      refresherConfig
    )
  }
}

case class CertificateConfig(
  provider: String,
  alias: String,
  commonName: String,
  logicalNames: List[String],
  minValidDays: Int
)

object CertificateConfig {

  val defaultMinValidDays = 10

  def fromConfig(alias: String, cfg: Config) : CertificateConfig = {
    val provider = cfg.getString("provider", "default")
    val commonName = cfg.getString("commonName")
    val logicalNames = cfg.getStringList("logicalHostnames", List.empty)
    val minValidDays = cfg.getInt("minValidDays", defaultMinValidDays)

    CertificateConfig(provider, alias, commonName, logicalNames, minValidDays)
  }
}

case class RefresherConfig(
  minValidDays: Int,
  hourOfDay: Int,
  minuteOfDay: Int,
  onRefreshAction: RefresherConfig.Action)

object RefresherConfig {

  sealed trait Action
  object Action {
    def fromString(action: String): Try[Action] = Try {
      action match {
        case "refresh" => Refresh
        case "restart" => Restart
        case _ => sys.error("Unsupported action name: " + action)
      }
    }
  }
  case object Refresh extends Action
  case object Restart extends Action

  def fromConfig(config: Config): Try[RefresherConfig] = Try {
    RefresherConfig(
      minValidDays = config.getInt("minValidDays", CertificateConfig.defaultMinValidDays),
      hourOfDay = config.getInt("hour", 0),
      minuteOfDay = config.getInt("minute", 0),
      onRefreshAction = RefresherConfig.Action.fromString(config.getString("onRefreshAction", "refresh")).get)
  }
}