package blended.jms.bridge.internal

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{JmsDestination, JmsDurableTopic, JmsQueue, JmsTopic}
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

    val outDest = JmsDestination.create(resolve(cfg.getString("to"))).get

    val persistent = cfg.getString("persistent", "passthrough")

    val listener = cfg.getInt("listener", 2)

    InboundConfig(
      name = name,
      vendor = vendor,
      provider = provider,
      from = inDest,
      selector = selector,
      to = outDest,
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
  to : JmsDestination,
  persistent : String,
  listener : Int
) extends ProviderAware
