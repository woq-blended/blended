package blended.security.ssl.internal

import blended.container.context.api.ContainerIdentifierService
import blended.security.ssl.CommonNameProvider
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.util.Try

object KeystoreConfig {

  def fromConfig(cfg : Config, hasher : PasswordHasher) : KeystoreConfig = {
    val keyStore = cfg.getString("keyStore", System.getProperty("javax.net.ssl.keyStore"))
    val storePass = cfg.getString("storePass", System.getProperty("javax.net.ssl.keyStorePassword"))
    val keyPass = cfg.getString("keyPass", System.getProperty("javax.net.ssl.keyPassword"))

    KeystoreConfig(
      keyStore,
      hasher.password(storePass),
      hasher.password(keyPass)
    )
  }
}

case class KeystoreConfig(
  keyStore : String,
  storePass : String,
  keyPass : String
)

/**
 * Configuration of [[CertificateManagerImpl]]
 *
 * @param keyStore The used keyStore.
 * @param storePass The password used to open the key store.
 * @param keyPass The key password.
 *   If the days until the end of the certificate validity fall below this threshold,
 *   the [[CertificateManagerImpl]] will try to re-new the certificate.
 * @param skipInitialCheck If `true` no initial certifcate check will be issues.
 */
case class CertificateManagerConfig(
  clientOnly : Boolean,
  maintainTruststore : Boolean,
  keystoreCfg : Option[KeystoreConfig],
  providerList : List[String],
  certConfigs : List[CertificateConfig],
  refresherConfig : Option[RefresherConfig],
  skipInitialCheck : Boolean,
  validCypherSuites : List[String]
)

object CertificateManagerConfig {

  /**
   * Read a [[CertificateManagerConfig]] from a typesafe [[Config]],
   * using the given [[PasswordHasher]] to hash the passwords (`keyPass` and `storePass`).
   */
  def fromConfig(cfg : Config, hasher : PasswordHasher, idSvc : ContainerIdentifierService) : CertificateManagerConfig = {

    val clientOnly : Boolean = cfg.getBoolean("clientOnly", false)

    val maintainTruststore : Boolean = cfg.getBoolean("maintainTruststore", true)

    val keystoreCfg : Option[KeystoreConfig] = if (clientOnly) {
      None
    } else {
      Some(KeystoreConfig.fromConfig(cfg, hasher))
    }

    val providers : List[String] = cfg.getStringList("providerList", List.empty)

    val certConfigs = cfg.getConfigMap("certificates", Map.empty).map {
      case (k, v) =>
        CertificateConfig.fromConfig(k, v, idSvc)
    }.toList

    val refresherConfig = cfg.getConfigOption("refresher").map(c => RefresherConfig.fromConfig(c).get)

    val skipInitialCheck = cfg.getBoolean("skipInitialCheck", false)

    val cyphers = cfg.getStringList("validCypherSuites", List.empty)

    CertificateManagerConfig(
      clientOnly = clientOnly,
      maintainTruststore = maintainTruststore,
      keystoreCfg = keystoreCfg,
      certConfigs = certConfigs,
      refresherConfig = refresherConfig,
      skipInitialCheck = skipInitialCheck,
      providerList = providers,
      validCypherSuites = cyphers
    )
  }
}

case class CertificateConfig(
  provider : String,
  alias : String,
  minValidDays : Int,
  cnProvider : CommonNameProvider
)

object CertificateConfig {

  val defaultMinValidDays = 10

  def fromConfig(alias : String, cfg : Config, idSvc : ContainerIdentifierService) : CertificateConfig = {
    val provider = cfg.getString("provider", "default")
    val minValidDays = cfg.getInt("minValidDays", defaultMinValidDays)

    CertificateConfig(provider, alias, minValidDays, new ConfigCommonNameProvider(cfg, idSvc))
  }
}

case class RefresherConfig(
  minValidDays : Int,
  hourOfDay : Int,
  minuteOfDay : Int,
  onRefreshAction : RefresherConfig.Action
)

object RefresherConfig {

  sealed trait Action
  object Action {
    def fromString(action : String) : Try[Action] = Try {
      action match {
        case "refresh" => Refresh
        case "restart" => Restart
        case _         => sys.error("Unsupported action name: " + action)
      }
    }
  }
  case object Refresh extends Action
  case object Restart extends Action

  def fromConfig(config : Config) : Try[RefresherConfig] = Try {
    RefresherConfig(
      minValidDays = config.getInt("minValidDays", CertificateConfig.defaultMinValidDays),
      hourOfDay = config.getInt("hour", 0),
      minuteOfDay = config.getInt("minute", 0),
      onRefreshAction = RefresherConfig.Action.fromString(config.getString("onRefreshAction", "refresh")).get
    )
  }
}
