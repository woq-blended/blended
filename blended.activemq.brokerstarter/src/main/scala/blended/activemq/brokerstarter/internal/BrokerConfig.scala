package blended.activemq.brokerstarter.internal

import blended.container.context.api.ContainerIdentifierService
import com.typesafe.config.Config
import blended.util.config.Implicits._

import scala.util.Try

case class BrokerConfig (
  vendor : String,
  provider : String,
  brokerName : String,
  file : String,
  clientId : String,
  withSsl : Boolean
)

object BrokerConfig {

  def create(brokerName : String, idSvc: ContainerIdentifierService, cfg: Config) : Try[BrokerConfig] = Try {

    def resolve(value: String) : String = idSvc.resolvePropertyString(value).get

    val name = resolve(cfg.getString("brokerName", brokerName))
    val provider = resolve(cfg.getString("provider", brokerName))
    val file = resolve(cfg.getString("file", s"$brokerName.amq"))
    val clientId = resolve(cfg.getString("clientId"))
    val ssl = cfg.getBoolean("withSsl", true)

    BrokerConfig(
      vendor = "activemq",
      provider = provider,
      brokerName = name,
      file = file,
      clientId = clientId,
      withSsl = ssl
    )
  }
}
