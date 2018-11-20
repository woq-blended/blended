package blended.jms.utils

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.ConnectionFactoryActivator.{CF_JNDI_NAME, DEFAULT_PWD, DEFAULT_USER, USE_JNDI}
import blended.updater.config.util.ConfigPropertyMapConverter
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object BlendedJMSConnectionConfig {

  val defaultConfig = BlendedJMSConnectionConfig(
    vendor = "",
    provider = "",
    enabled = true,
    jmxEnabled = true,
    pingEnabled = true,
    pingTolerance = 5,
    pingInterval = 30.seconds,
    pingTimeout = 3.seconds,
    retryInterval = 5.seconds,
    minReconnect = 5.minutes,
    maxReconnectTimeout = None,
    clientId = "$[[" + ContainerIdentifierService.containerId + "]]",
    defaultUser = None,
    defaultPassword  = None,
    pingDestination = "topic:blended.ping",
    properties = Map.empty,
    useJndi = false,
    jndiName = None,
    cfEnabled = None,
    cfClassName = None,
    ctxtClassName = None,
    jmsClassloader  = None
  )

  val enabled : Config => Boolean = cfg => cfg.getBoolean("enabled", defaultConfig.enabled)
  val jmxEnabled : Config => Boolean = cfg => cfg.getBoolean("jmxEnabled", defaultConfig.jmxEnabled)
  val pingEnabled : Config => Boolean = cfg => cfg.getBoolean("pingEnabled", defaultConfig.pingEnabled)
  val pingTolerance : Config => Int = cfg => cfg.getInt("pingTolerance", defaultConfig.pingTolerance)
  val pingInterval : Config => FiniteDuration = cfg => cfg.getDuration("pingInterval", defaultConfig.pingInterval)
  val pingTimeout : Config => FiniteDuration = cfg => cfg.getDuration("pingTimeout", defaultConfig.pingTimeout)
  val retryInterval : Config => FiniteDuration = cfg => cfg.getDuration("retryInterval", defaultConfig.retryInterval)
  val minReconnect : Config => FiniteDuration = cfg => cfg.getDuration("minReconnect", defaultConfig.minReconnect)
  val maxReconnectTimeout : Config => Option[FiniteDuration] = cfg => cfg.getDurationOption("maxReconnectTimeout")

  val defaultUser : Config => Option[String] = cfg => cfg.getStringOption(DEFAULT_USER)
  val defaultPasswd : Config => Option[String] = cfg => cfg.getStringOption(DEFAULT_PWD)
  val destination : Config => String = cfg => cfg.getString("destination", defaultConfig.pingDestination)

  val properties : (String => Try[Any]) => Config => Try[Map[String, String]] = stringResolver => cfg => Try {
    ConfigPropertyMapConverter.getKeyAsPropertyMap(cfg, "properties", Option(() => defaultConfig.properties))
      .mapValues { v =>
        stringResolver(v) match {
          case Failure(t) => throw t
          case Success(s) => s.toString()
        }
      }
  }

  val jndiName : Config => Option[String] = cfg => cfg.getStringOption(CF_JNDI_NAME)
  val useJndi : Config => Boolean = cfg => cfg.getBoolean(USE_JNDI, defaultConfig.useJndi)

  val clientId : (String => Try[Any]) => Config => Try[String] = stringResolver => cfg => Try {
    cfg.getStringOption("clientId") match {
      case None => defaultConfig.clientId
      case Some(s) => stringResolver(s).map(_.toString).get
    }
  }

  def fromConfig(stringResolver : String => Try[Any])(vendor: String, provider: String, cfg: Config) : BlendedJMSConnectionConfig = {

    BlendedJMSConnectionConfig(
      vendor = vendor,
      enabled = enabled(cfg),
      provider = provider,
      jmxEnabled = jmxEnabled(cfg),
      pingEnabled = pingEnabled(cfg),
      pingTolerance = pingTolerance(cfg),
      pingInterval = pingInterval(cfg),
      pingTimeout = pingTimeout(cfg),
      retryInterval = retryInterval(cfg),
      minReconnect = minReconnect(cfg),
      maxReconnectTimeout = maxReconnectTimeout(cfg),
      clientId = clientId(stringResolver)(cfg).get,
      defaultUser = defaultUser(cfg),
      defaultPassword = defaultPasswd(cfg),
      pingDestination = destination(cfg),
      properties = properties(stringResolver)(cfg).get,
      jndiName = jndiName(cfg),
      useJndi = useJndi(cfg),
      cfEnabled = None,
      jmsClassloader = defaultConfig.jmsClassloader,
      ctxtClassName = defaultConfig.ctxtClassName,
      cfClassName = defaultConfig.cfClassName
    )
  }
}

case class BlendedJMSConnectionConfig(
  override val vendor : String,
  override val provider : String,
  override val enabled : Boolean,
  override val jmxEnabled : Boolean,
  override val pingEnabled : Boolean,
  override val pingTolerance : Int,
  override val pingInterval : FiniteDuration,
  override val pingTimeout : FiniteDuration,
  override val retryInterval : FiniteDuration,
  override val minReconnect : FiniteDuration,
  override val maxReconnectTimeout: Option[FiniteDuration],
  override val clientId : String,
  override val defaultUser : Option[String],
  override val defaultPassword : Option[String],
  override val pingDestination : String,
  override val properties : Map[String, String],
  override val useJndi : Boolean,
  override val jndiName : Option[String] = None,
  override val cfEnabled : Option[ConnectionConfig => Boolean],
  override val cfClassName: Option[String],
  override val ctxtClassName : Option[String],
  override val jmsClassloader : Option[ClassLoader]
) extends ConnectionConfig
