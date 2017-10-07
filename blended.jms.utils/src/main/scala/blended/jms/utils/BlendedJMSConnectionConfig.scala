package blended.jms.utils

import blended.container.context.ContainerIdentifierService
import blended.jms.utils.ConnectionFactoryActivator.{CF_JNDI_NAME, DEFAULT_PWD, DEFAULT_USER, USE_JNDI}
import com.typesafe.config.Config

import scala.collection.JavaConverters._

object BlendedJMSConnectionConfig {

  val defaultConfig = BlendedJMSConnectionConfig(
    vendor = "",
    provider = "",
    enabled = true,
    jmxEnabled = true,
    pingTolerance = 5,
    pingInterval = 30,
    pingTimeout = 3,
    retryInterval = 5,
    minReconnect = 300,
    maxReconnectTimeout = -1,
    clientId = "$[[" + ContainerIdentifierService.containerId + "]]",
    defaultUser = None,
    defaultPassword  = None,
    pingDestination = "blended.ping",
    properties = Map.empty
  )

  def apply(vendor: String, cfg: Config): BlendedJMSConnectionConfig = apply(vendor = vendor, provider = None, cfg = cfg)

  def apply(vendor: String, provider: Option[String], cfg: Config) : BlendedJMSConnectionConfig = {
    val prov = if (cfg.hasPath("provider")) cfg.getString("provider") else provider.getOrElse(defaultConfig.provider)
    val enabled = !cfg.hasPath("enabled") || cfg.getBoolean("enabled")
    val jmxEnabled = if (cfg.hasPath("jmxEnabled")) cfg.getBoolean("jmxEnabled") else defaultConfig.jmxEnabled
    val pingTolerance = if (cfg.hasPath("pingTolerance")) cfg.getInt("pingTolerance") else defaultConfig.pingTolerance
    val pingInterval = if (cfg.hasPath("pingInterval")) cfg.getInt("pingInterval") else defaultConfig.pingInterval
    val pingTimeout = if (cfg.hasPath("pingTimeout")) cfg.getInt("pingTimeout") else defaultConfig.pingTimeout
    val retryInterval = if (cfg.hasPath("retryInterval")) cfg.getInt("retryInterval") else defaultConfig.retryInterval
    val minReconnect = if (cfg.hasPath("minReconnect")) cfg.getInt("minReconnect") else defaultConfig.minReconnect
    val maxReconnectTimeout = if (cfg.hasPath("maxReconnectTimeout")) cfg.getInt("maxReconnectTimeout") else defaultConfig.maxReconnectTimeout
    val clientId = if (cfg.hasPath("clientId")) cfg.getString("clientId") else defaultConfig.clientId
    val defaultUser = if (cfg.hasPath(DEFAULT_USER)) Some(cfg.getString(DEFAULT_USER)) else defaultConfig.defaultUser
    val defaultPasswd = if (cfg.hasPath(DEFAULT_PWD)) Some(cfg.getString(DEFAULT_PWD)) else defaultConfig.defaultPassword
    val destination = if (cfg.hasPath("destination")) cfg.getString("destination") else defaultConfig.pingDestination
    val properties : Map[String, String] = if (cfg.hasPath("properties")) {
      cfg.getConfig("properties").entrySet().asScala.map{ e =>
        (e.getKey(), cfg.getConfig("properties").getString(e.getKey()))
      }.toMap
    } else Map.empty
    val jndiName = if (cfg.hasPath(CF_JNDI_NAME)) Some(cfg.getString(CF_JNDI_NAME)) else None
    val useJndi = if (cfg.hasPath(USE_JNDI)) cfg.getBoolean(USE_JNDI) else false

    BlendedJMSConnectionConfig(
      vendor = vendor,
      enabled = enabled,
      provider = prov,
      jmxEnabled = jmxEnabled,
      pingTolerance = pingTolerance,
      pingInterval = pingInterval,
      pingTimeout = pingTimeout,
      retryInterval = retryInterval,
      minReconnect = minReconnect,
      maxReconnectTimeout = maxReconnectTimeout,
      clientId = clientId,
      defaultUser = defaultUser,
      defaultPassword = defaultPasswd,
      pingDestination = destination,
      properties = properties,
      jndiName = jndiName,
      useJndi = useJndi
    )
  }
}

case class BlendedJMSConnectionConfig(
  vendor : String,
  provider : String,
  enabled : Boolean,
  jmxEnabled : Boolean,
  pingTolerance : Int,
  pingInterval : Int,
  pingTimeout : Int,
  retryInterval : Int,
  minReconnect : Int,
  maxReconnectTimeout: Int,
  clientId : String,
  defaultUser : Option[String],
  defaultPassword : Option[String],
  pingDestination : String,
  properties : Map[String, String],
  useJndi : Boolean = false,
  jndiName : Option[String] = None,
  cfEnabled : Option[BlendedJMSConnectionConfig => Boolean] = None,
  cfClassName: Option[String] = None,
  ctxtClassName : Option[String] = None,
  jmsClassloader : Option[ClassLoader] = None
)
