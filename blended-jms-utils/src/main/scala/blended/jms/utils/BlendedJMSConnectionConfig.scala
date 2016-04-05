package blended.jms.utils

import com.typesafe.config.Config

object BlendedJMSConnectionConfig {

  val defaultConfig = BlendedJMSConnectionConfig(5, 30, 3, 5, 300)

  def apply(cfg: Config) : BlendedJMSConnectionConfig = {
    val pingTolerance = if (cfg.hasPath("pingTolerance")) cfg.getInt("pingTolerance") else defaultConfig.pingTolerance
    val pingInterval  = if (cfg.hasPath("pingInterval")) cfg.getInt("pingInterval") else defaultConfig.pingInterval
    val pingTimeout   = if (cfg.hasPath("pingTimeout")) cfg.getInt("pingTimeout") else defaultConfig.pingTimeout
    val retryInterval = if (cfg.hasPath("retryInterval")) cfg.getInt("retryInterval") else defaultConfig.retryInterval
    val minReconnect  = if (cfg.hasPath("minReconnect")) cfg.getInt("minReconnect") else defaultConfig.minReconnect

    BlendedJMSConnectionConfig(pingTolerance, pingInterval, pingTimeout, retryInterval, minReconnect)
  }
}

case class BlendedJMSConnectionConfig(
  pingTolerance : Int,
  pingInterval : Int,
  pingTimeout : Int,
  retryInterval : Int,
  minReconnect : Int
)
