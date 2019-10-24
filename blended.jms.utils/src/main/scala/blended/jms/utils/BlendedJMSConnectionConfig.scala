package blended.jms.utils

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.ConnectionFactoryActivator.{CF_JNDI_NAME, DEFAULT_PWD, DEFAULT_USER, USE_JNDI}
import blended.updater.config.util.ConfigPropertyMapConverter
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import blended.util.RichTry._

object BlendedJMSConnectionConfig {

  //scalastyle:off magic.number
  val defaultConfig = BlendedJMSConnectionConfig(
    vendor = "",
    provider = "",
    enabled = true,
    jmxEnabled = true,
    keepAliveEnabled = true,
    maxKeepAliveMissed = 5,
    keepAliveInterval = 60.seconds,
    minReconnect = 5.minutes,
    maxReconnectTimeout = None,
    connectTimeout = 30.seconds,
    retryInterval = 5.seconds,
    clientId = "$[[" + ContainerIdentifierService.containerId + "]]",
    defaultUser = None,
    defaultPassword = None,
    keepAliveDestination = "topic:blended.ping",
    keepAliveReceiveOnly = false,
    properties = Map.empty,
    useJndi = false,
    jndiName = None,
    cfEnabled = None,
    cfClassName = None,
    ctxtClassName = None,
    jmsClassloader = None
  )
  //scalastyle:on magic.number

  // scalastyle:off method.length
  def fromConfig(
    stringResolver : String => Try[Any]
  )(
    vendor : String, provider : String, cfg : Config
  ) : BlendedJMSConnectionConfig = {

    val enabled : Config => Boolean = cfg => cfg.getBoolean("enabled", defaultConfig.enabled)
    val jmxEnabled : Config => Boolean = cfg => cfg.getBoolean("jmxEnabled", defaultConfig.jmxEnabled)
    val keepAliveEnabled : Config => Boolean = cfg => cfg.getBoolean("keepAliveEnabled", defaultConfig.keepAliveEnabled)
    val maxKeepAliveMissed : Config => Int = cfg => cfg.getInt("maxKeepAliveMissed", defaultConfig.maxKeepAliveMissed)
    val keepAliveInterval : Config => FiniteDuration = cfg => cfg.getDuration("keepAliveInterval", defaultConfig.keepAliveInterval)
    val minReconnect : Config => FiniteDuration = cfg => cfg.getDuration("minReconnect", defaultConfig.minReconnect)
    val maxReconnectTimeout : Config => Option[FiniteDuration] = cfg => cfg.getDurationOption("maxReconnectTimeout")
    val connectTimeout : Config => FiniteDuration = cfg => cfg.getDuration("connectTimeout", defaultConfig.connectTimeout)
    val retryInterval : Config => FiniteDuration = cfg => cfg.getDuration("retryInterval", defaultConfig.retryInterval)

    val defaultUser : Config => Option[String] = cfg => cfg.getStringOption(DEFAULT_USER).map { u => stringResolver(u).get }.map(_.toString)
    val defaultPasswd : Config => Option[String] = cfg => cfg.getStringOption(DEFAULT_PWD).map { p => stringResolver(p).get }.map(_.toString)
    val destination : Config => String = cfg => cfg.getString("destination", defaultConfig.keepAliveDestination)

    val pingReceiveOnly : Config => Boolean = cfg => cfg.getBoolean("pingReceiveOnly", false)

    val properties : (String => Try[Any]) => Config => Try[Map[String, String]] = stringResolver => cfg => Try {
      ConfigPropertyMapConverter.getKeyAsPropertyMap(cfg, "properties", Option(() => defaultConfig.properties))
        .mapValues { v =>
          stringResolver(v) match {
            case Failure(t) => throw t
            case Success(s) => s.toString()
          }
        }
    }

    val clientId : Config => Try[String] = cfg => Try {
      cfg.getStringOption("clientId") match {
        case None    => defaultConfig.clientId
        case Some(s) => stringResolver(s).map(_.toString).unwrap
      }
    }

    val jndiName : Config => Option[String] = cfg => cfg.getStringOption(CF_JNDI_NAME)
    val useJndi : Config => Boolean = cfg => cfg.getBoolean(USE_JNDI, defaultConfig.useJndi)

    BlendedJMSConnectionConfig(
      vendor = vendor,
      enabled = enabled(cfg),
      provider = provider,
      jmxEnabled = jmxEnabled(cfg),
      keepAliveEnabled = keepAliveEnabled(cfg),
      maxKeepAliveMissed = maxKeepAliveMissed(cfg),
      keepAliveInterval = keepAliveInterval(cfg),
      minReconnect = minReconnect(cfg),
      maxReconnectTimeout = maxReconnectTimeout(cfg),
      retryInterval = retryInterval(cfg),
      connectTimeout = connectTimeout(cfg),
      clientId = clientId(cfg).get,
      defaultUser = defaultUser(cfg),
      defaultPassword = defaultPasswd(cfg),
      keepAliveDestination = destination(cfg),
      keepAliveReceiveOnly = pingReceiveOnly(cfg),
      properties = properties(stringResolver)(cfg).get,
      jndiName = jndiName(cfg),
      useJndi = useJndi(cfg),
      cfEnabled = None,
      jmsClassloader = defaultConfig.jmsClassloader,
      ctxtClassName = defaultConfig.ctxtClassName,
      cfClassName = defaultConfig.cfClassName
    )
  }
  // scalastyle:on method.length
}

case class BlendedJMSConnectionConfig(
  override val vendor : String,
  override val provider : String,
  override val enabled : Boolean,
  override val jmxEnabled : Boolean,
  override val keepAliveEnabled : Boolean,
  override val maxKeepAliveMissed : Int,
  override val keepAliveInterval: FiniteDuration,
  override val minReconnect : FiniteDuration,
  override val connectTimeout: FiniteDuration,
  override val maxReconnectTimeout: Option[FiniteDuration],
  override val retryInterval : FiniteDuration,
  override val clientId : String,
  override val defaultUser : Option[String],
  override val defaultPassword : Option[String],
  override val keepAliveDestination : String,
  override val keepAliveReceiveOnly : Boolean,
  override val properties : Map[String, String],
  override val useJndi : Boolean,
  override val jndiName : Option[String] = None,
  override val cfEnabled : Option[ConnectionConfig => Boolean],
  override val cfClassName : Option[String],
  override val ctxtClassName : Option[String],
  override val jmsClassloader : Option[ClassLoader]
) extends ConnectionConfig
