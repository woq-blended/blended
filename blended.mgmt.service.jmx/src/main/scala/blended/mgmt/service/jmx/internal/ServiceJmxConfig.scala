package blended.mgmt.service.jmx.internal

import com.typesafe.config.Config

object ServiceJmxConfig {

  val intervalPath = "interval"

  def apply(cfg: Config) : ServiceJmxConfig = {
    new ServiceJmxConfig(
      interval = if (cfg.hasPath(intervalPath)) cfg.getInt(intervalPath) else 5
    )
  }
}

case class ServiceJmxConfig(

  interval : Int
)

