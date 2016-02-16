package blended.jms.utils

import com.typesafe.config.Config

object BlendedJMSConnectionConfig {

  val defaultConfig = BlendedJMSConnectionConfig(5, 30, 3, 5)

  def apply(cfg: Config) : BlendedJMSConnectionConfig = {
    val pingTolerance = if (cfg.hasPath("pingTolerance")) cfg.getInt("pingTolerance") else defaultConfig.pingTolerance
    val pingInterval  = if (cfg.hasPath("pingInterval")) cfg.getInt("pingInterval") else defaultConfig.pingInterval
    val pingTimeout   = if (cfg.hasPath("pingTimeout")) cfg.getInt("pingTimeout") else defaultConfig.pingTimeout
    val retryInterval = if (cfg.hasPath("retryInterval")) cfg.getInt("retryInterval") else defaultConfig.retryInterval

    BlendedJMSConnectionConfig(pingTolerance, pingInterval, pingTimeout, retryInterval)
  }
}

case class BlendedJMSConnectionConfig(
  pingTolerance : Int,
  pingInterval : Int,
  pingTimeout : Int,
  retryInterval : Int
)
