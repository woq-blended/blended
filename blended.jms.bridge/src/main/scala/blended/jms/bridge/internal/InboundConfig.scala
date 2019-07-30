package blended.jms.bridge.internal

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils._
import blended.streams.jms.JmsDeliveryMode
import blended.streams.processor.HeaderProcessorConfig
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.util.Try

object InboundConfig {

  def create(idSvc : ContainerIdentifierService, cfg : Config) : Try[InboundConfig] = Try {

    def resolve(value : String) : String = idSvc.resolvePropertyString(value).map(_.toString()).get

    val name : String = resolve(cfg.getString("name"))
    val vendor : String = resolve(cfg.getString("vendor"))
    val provider : Option[String] = cfg.getStringOption("provider").map(resolve)

    val subscriberName : Option[String] = cfg.getStringOption("subscriberName").map(resolve)

    val inDest : JmsDestination = JmsDestination.create(resolve(cfg.getString("from"))).get match {
      case q : JmsQueue => q
      case t : JmsTopic => subscriberName match  {
        case Some(sn) => JmsDurableTopic(t.name, sn)
        case None     => t
      }
      case t : JmsDurableTopic => t
    }

    val selector : Option[String] = cfg.getStringOption("selector").map(resolve)
    val persistent : JmsDeliveryMode = JmsDeliveryMode.create(cfg.getString("persistent", JmsDeliveryMode.Persistent.asString)).get


    val listener : Int = cfg.getInt("listener", 2)

    val header : List[HeaderProcessorConfig] = cfg.getConfigList("header", List.empty).map { cfg =>
      HeaderProcessorConfig.create(cfg)
    }

    val sessionRecreateTimeout : FiniteDuration = cfg.getDuration("sessionRecreateTimeout", 1.second)

    InboundConfig(
      name = name,
      vendor = vendor,
      provider = provider,
      from = inDest,
      selector = selector,
      persistent = persistent,
      subscriberName = subscriberName,
      listener = listener,
      header = header,
      sessionRecreateTimeout = sessionRecreateTimeout
    )
  }
}

case class InboundConfig(
  name : String,
  vendor : String,
  provider : Option[String],
  from : JmsDestination,
  selector : Option[String],
  persistent : JmsDeliveryMode,
  subscriberName : Option[String],
  listener : Int,
  header : List[HeaderProcessorConfig],
  sessionRecreateTimeout : FiniteDuration
)
