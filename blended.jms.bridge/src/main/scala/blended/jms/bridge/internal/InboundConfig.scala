package blended.jms.bridge.internal

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils._
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.util.Try

object InboundConfig {

  def create(idSvc : ContainerIdentifierService, cfg: Config): Try[InboundConfig] = Try {

    def resolve(value: String) : String = idSvc.resolvePropertyString(value).get

    val name = resolve(cfg.getString("name"))
    val vendor = resolve(cfg.getString("vendor"))
    val provider = cfg.getStringOption("provider").map(resolve)

    val inDest = JmsDestination.create(resolve(cfg.getString("from"))).get match {
      case q : JmsQueue => q
      case t : JmsTopic => cfg.getStringOption("subscriberName") match  {
        case Some(sn) => JmsDurableTopic(t.name, sn)
        case None => t
      }
      case t : JmsDurableTopic => t
    }

    val selector = cfg.getStringOption("selector")

    val persistent = cfg.getString("persistent", "passthrough")

    val listener = cfg.getInt("listener", 2)

    InboundConfig(
      name = name,
      vendor = vendor,
      provider = provider,
      from = inDest,
      selector = selector,
      persistent = persistent,
      listener = listener
    )
  }
}

case class InboundConfig (
  name : String,
  vendor : String,
  provider : Option[String],
  from : JmsDestination,
  selector : Option[String],
  persistent : String,
  listener : Int
)
