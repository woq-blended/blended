package blended.jms.utils

import com.typesafe.config.Config

object BlendedJMSConnectionConfig {

  val defaultConfig = BlendedJMSConnectionConfig(
    pingTolerance = 5,
    pingInterval = 30,
    pingTimeout = 3,
    retryInterval = 5,
    minReconnect = 300,
    maxReconnectTimeout = -1
  )

  def apply(cfg: Config) : BlendedJMSConnectionConfig = {
    val pingTolerance = if (cfg.hasPath("pingTolerance")) cfg.getInt("pingTolerance") else defaultConfig.pingTolerance
    val pingInterval  = if (cfg.hasPath("pingInterval")) cfg.getInt("pingInterval") else defaultConfig.pingInterval
    val pingTimeout   = if (cfg.hasPath("pingTimeout")) cfg.getInt("pingTimeout") else defaultConfig.pingTimeout
    val retryInterval = if (cfg.hasPath("retryInterval")) cfg.getInt("retryInterval") else defaultConfig.retryInterval
    val minReconnect  = if (cfg.hasPath("minReconnect")) cfg.getInt("minReconnect") else defaultConfig.minReconnect
    val maxReconnectTimeout = if (cfg.hasPath("maxReconnectTimeout")) cfg.getInt("maxReconnectTimeout") else defaultConfig.maxReconnectTimeout

    BlendedJMSConnectionConfig(
      pingTolerance = pingTolerance,
      pingInterval = pingInterval,
      pingTimeout = pingTimeout,
      retryInterval = retryInterval,
      minReconnect = minReconnect,
      maxReconnectTimeout = maxReconnectTimeout
    )
  }
}

case class BlendedJMSConnectionConfig(
  pingTolerance : Int,
  pingInterval : Int,
  pingTimeout : Int,
  retryInterval : Int,
  minReconnect : Int,
  maxReconnectTimeout: Int
)
