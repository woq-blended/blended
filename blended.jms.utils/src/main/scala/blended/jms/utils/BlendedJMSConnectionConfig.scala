package blended.jms.utils

import blended.container.context.ContainerIdentifierService
import com.typesafe.config.Config

object BlendedJMSConnectionConfig {

  val defaultConfig = BlendedJMSConnectionConfig(
    jmxEnabled = true,
    enabled = true,
    pingTolerance = 5,
    pingInterval = 30,
    pingTimeout = 3,
    retryInterval = 5,
    minReconnect = 300,
    maxReconnectTimeout = -1,
    clientId = ContainerIdentifierService.containerId
  )

  def apply(cfg: Config) : BlendedJMSConnectionConfig = {
    val jmxEnabled = if (cfg.hasPath("jmxEnabled")) cfg.getBoolean("jmxEnabled") else defaultConfig.jmxEnabled
    val enabled = if (cfg.hasPath("enabled")) cfg.getBoolean("enabled") else defaultConfig.enabled
    val pingTolerance = if (cfg.hasPath("pingTolerance")) cfg.getInt("pingTolerance") else defaultConfig.pingTolerance
    val pingInterval = if (cfg.hasPath("pingInterval")) cfg.getInt("pingInterval") else defaultConfig.pingInterval
    val pingTimeout = if (cfg.hasPath("pingTimeout")) cfg.getInt("pingTimeout") else defaultConfig.pingTimeout
    val retryInterval = if (cfg.hasPath("retryInterval")) cfg.getInt("retryInterval") else defaultConfig.retryInterval
    val minReconnect = if (cfg.hasPath("minReconnect")) cfg.getInt("minReconnect") else defaultConfig.minReconnect
    val maxReconnectTimeout = if (cfg.hasPath("maxReconnectTimeout")) cfg.getInt("maxReconnectTimeout") else defaultConfig.maxReconnectTimeout
    val clientId = if (cfg.hasPath("clientId")) cfg.getString("clientId") else defaultConfig.clientId

    BlendedJMSConnectionConfig(
      jmxEnabled = jmxEnabled,
      enabled = enabled,
      pingTolerance = pingTolerance,
      pingInterval = pingInterval,
      pingTimeout = pingTimeout,
      retryInterval = retryInterval,
      minReconnect = minReconnect,
      maxReconnectTimeout = maxReconnectTimeout,
      clientId = clientId
    )
  }
}

case class BlendedJMSConnectionConfig(
  jmxEnabled : Boolean,
  enabled : Boolean,
  pingTolerance : Int,
  pingInterval : Int,
  pingTimeout : Int,
  retryInterval : Int,
  minReconnect : Int,
  maxReconnectTimeout: Int,
  clientId : String
)
