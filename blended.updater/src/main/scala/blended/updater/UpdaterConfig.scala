package blended.updater

import blended.util.config.Implicits._
import com.typesafe.config.Config

object UpdaterConfig {

  val default : UpdaterConfig = {
    UpdaterConfig(
      serviceInfoIntervalMSec = 0,
      serviceInfoLifetimeMSec = 0
    )

  }

  def fromConfig(cfg : Config) : UpdaterConfig = {
    UpdaterConfig(
      serviceInfoIntervalMSec = cfg.getLong("serviceInfoIntervalMSec", default.serviceInfoIntervalMSec),
      serviceInfoLifetimeMSec = cfg.getLong("serviceInfoLifetimeMSec", default.serviceInfoLifetimeMSec)
    )
  }
}

/**
 * Configuration for [Updater] actor.
 *
 * @param serviceInfoIntervalMSec Interval in milliseconds to publish a ServiceInfo message to the Akka event stream.
 *        An value of zero (0) or below indicates that no such information should be published.
 * @param serviceInfoLifetimeMSec The lifetime a serviceInfo message should be valid.
 */
case class UpdaterConfig(
  serviceInfoIntervalMSec : Long,
  serviceInfoLifetimeMSec : Long,
)
