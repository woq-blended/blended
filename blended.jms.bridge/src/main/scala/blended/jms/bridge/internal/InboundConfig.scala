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

    val outDest = JmsDestination.create(resolve(cfg.getString("to"))).get

    val listener = cfg.getInt("listener", 5)

    new InboundConfig(
      name = name,
      vendor = vendor,
      provider = provider,
      from = inDest,
      to = outDest,
      listener
    )
  }
}

case class InboundConfig (
  name : String,
  vendor : String,
  provider : Option[String],
  from : JmsDestination,
  to : JmsDestination,
  listener: Int
) extends ProviderAware
