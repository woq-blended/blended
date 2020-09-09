package blended.security.ssl.internal

import blended.container.context.api.ContainerContext
import blended.security.ssl.CommonNameProvider
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigRenderOptions}

import scala.util.Try

object KeystoreConfig {

  private val log : Logger = Logger[KeystoreConfig.type]

  def fromConfig(cfg: Config, hasher: PasswordHasher, ctCtxt: ContainerContext): KeystoreConfig = {
    val keyStore = cfg.getString("keyStore", System.getProperty("javax.net.ssl.keyStore"))

    val storePassRaw : String = cfg.getStringOption("explicit.storePass")
      .orElse(cfg.getStringOption("storePass").map(hasher.password))
      .getOrElse(System.getProperty("javax.net.ssl.keyStorePassword"))

    log.trace(cfg.root().render(ConfigRenderOptions.concise().setFormatted(true)))

    val storePass: String = ctCtxt.resolveString(storePassRaw)
      .get.asInstanceOf[String]

    val keyPass: String = ctCtxt.resolveString(cfg
      .getStringOption("explicit.keyPass")
      .orElse(cfg.getStringOption("keyPass").map(hasher.password))
      .getOrElse(System.getProperty("javax.net.ssl.keyPassword")))
      .get.asInstanceOf[String]

    KeystoreConfig(
      keyStore,
      storePass,
      keyPass
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
 */
case class CertificateManagerConfig(
  clientOnly: Boolean,
  maintainTruststore: Boolean,
  keystoreCfg: Option[KeystoreConfig],
  providerList: List[String],
  certConfigs: List[CertificateConfig],
  refresherConfig: Option[RefresherConfig],
  skipInitialCheck: Boolean,
  validCypherSuites: List[String]
)

object CertificateManagerConfig {

  /**
   * Read a [[CertificateManagerConfig]] from a typesafe [[Config]],
   * using the given [[PasswordHasher]] to hash the passwords (`keyPass` and `storePass`).
   */
  def fromConfig(cfg : Config, hasher : PasswordHasher, ctCtxt : ContainerContext) : CertificateManagerConfig = {

    val clientOnly: Boolean = cfg.getBoolean("clientOnly", false)

    val maintainTruststore: Boolean = cfg.getBoolean("maintainTruststore", true)

    val keystoreCfg: Option[KeystoreConfig] = if (clientOnly) {
      None
    } else {
      Some(KeystoreConfig.fromConfig(cfg, hasher, ctCtxt))
    }

    val providers: List[String] = cfg.getStringList("providerList", List.empty)

    val certConfigs = cfg.getConfigMap("certificates", Map.empty).map {
      case (k, v) =>
        CertificateConfig.fromConfig(k, v, ctCtxt)
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

  def fromConfig(alias : String, cfg : Config, ctCtxt : ContainerContext) : CertificateConfig = {
    val provider = cfg.getString("provider", "default")
    val minValidDays = cfg.getInt("minValidDays", defaultMinValidDays)

    CertificateConfig(provider, alias, minValidDays, new ConfigCommonNameProvider(cfg, ctCtxt))
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
