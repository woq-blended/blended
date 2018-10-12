package blended.jms.bridge

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{JmsDestination, ProviderAware}
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.util.Try

case class BridgeProviderConfig(
  vendor : String,
  provider : String,
  internal: Boolean,
  inbound : JmsDestination,
  errorDestination : JmsDestination,
  eventDestination : JmsDestination
) extends ProviderAware {
  override def toString: String =
    s"${getClass().getSimpleName()}(vendor=$vendor, provider=$provider, internal=$internal, errorQueue=$errorDestination, eventQueue=$eventDestination)"
}

object BridgeProviderConfig {

  def create(idSvc: ContainerIdentifierService, cfg: Config) : Try[BridgeProviderConfig] = Try {

    def resolve(value: String) : String = idSvc.resolvePropertyString(value).get

    val errorQueue = resolve(cfg.getString("errorQueue", "blended.error"))
    val eventQueue = resolve(cfg.getString("eventQueue", "blended.event"))
    val inbound = cfg.getString("inbound")
    val vendor = resolve(cfg.getString("vendor"))
    val provider = resolve(cfg.getString("provider"))
    val internal = cfg.getBoolean("internal", false)

    BridgeProviderConfig(
      vendor = vendor,
      provider = provider,
      internal = internal,
      inbound = JmsDestination.create(inbound).get,
      errorDestination = JmsDestination.create(errorQueue).get,
      eventDestination = JmsDestination.create(eventQueue).get
    )
  }
}
