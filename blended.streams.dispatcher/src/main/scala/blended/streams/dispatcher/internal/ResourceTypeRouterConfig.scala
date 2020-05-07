package blended.streams.dispatcher.internal

import blended.container.context.api.ContainerContext
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.JmsDestination
import blended.streams.jms.JmsDeliveryMode
import blended.streams.processor.HeaderProcessorConfig
import blended.streams.worklist.WorklistItem
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try

object ResourceTypeRouterConfig {

  private[this] val defaultEventVendorPath = "defaultEventVendor"
  private[this] val defaultEventProviderPath = "defaultEventProvider"
  private[this] val applicationLogHeaderPath = "applicationLogHeader"
  private[this] val defaultHeaderPath = "defaultHeader"
  private[this] val resourcetypesPath = "resourcetypes"
  private[this] val startupPath = "onStartup"

  def create(
    ctCtxt : ContainerContext,
    provider : BridgeProviderRegistry,
    cfg : Config
  ) : Try[ResourceTypeRouterConfig] = Try {

    val internalProvider = provider.internalProvider.get

    val eventProvider = ProviderResolver.getProvider(
      provider,
      ctCtxt.resolveString(cfg.getString(defaultEventVendorPath, internalProvider.vendor)).map(_.toString).get,
      ctCtxt.resolveString(cfg.getString(defaultEventProviderPath, internalProvider.provider)).map(_.toString).get
    ).get

    val logHeader : List[String] = cfg.getStringList(applicationLogHeaderPath, List.empty)

    val routes : Map[String, ResourceTypeConfig] =
      cfg.getConfigMap(resourcetypesPath, Map.empty).map {
        case (key, value) =>
          key -> ResourceTypeConfig.create(
            ctCtxt = ctCtxt,
            registry = provider,
            resType = key,
            defaultProvider = internalProvider,
            defaultEventProvider = eventProvider,
            defaultLogHeader = logHeader,
            cfg = value
          ).get
      }

    val defaultHeader : List[HeaderProcessorConfig] = cfg.getConfigList(defaultHeaderPath, List.empty).map { cfg =>
      HeaderProcessorConfig.create(cfg)
    }

    val startupMap : Map[String, String] =
      cfg
        .getStringMapOption(startupPath)
        .getOrElse(Map.empty)
        .mapValues(s => ctCtxt.resolveString(s).get.toString)
        .toMap

    ResourceTypeRouterConfig(
      defaultProvider = internalProvider,
      eventProvider = eventProvider,
      applicationLogHeader = logHeader,
      defaultHeader = defaultHeader,
      resourceTypeConfigs = routes,
      providerRegistry = provider,
      startupMap = startupMap
    )
  }
}

case class ResourceTypeRouterConfig(
  defaultProvider : BridgeProviderConfig,
  eventProvider : BridgeProviderConfig,
  providerRegistry : BridgeProviderRegistry,
  applicationLogHeader : List[String],
  defaultHeader : List[HeaderProcessorConfig],
  resourceTypeConfigs : Map[String, ResourceTypeConfig],
  startupMap : Map[String, String]
)

object ResourceTypeConfig {

  private[this] val inboundPath = "inbound"
  private[this] val outboundPath = "outbound"
  private[this] val timeoutPath = "timeout"

  def create(
    ctCtxt : ContainerContext,
    registry : BridgeProviderRegistry,
    resType : String,
    defaultProvider : BridgeProviderConfig,
    defaultEventProvider : BridgeProviderConfig,
    defaultLogHeader : List[String],
    cfg : Config
  ) : Try[ResourceTypeConfig] = Try {

    val outboundRoutes : List[OutboundRouteConfig] = cfg.getConfigList(outboundPath).asScala.map { c =>
      OutboundRouteConfig.create(ctCtxt, registry, c, defaultProvider, defaultEventProvider, defaultLogHeader).get
    }.toList

    ResourceTypeConfig(
      resourceType = resType,
      withCBE = cfg.getBoolean("withCBE", true),
      outbound = outboundRoutes,
      inboundConfig = cfg.getConfigOption(inboundPath).map(c => InboundRouteConfig.create(ctCtxt, c).get),
      timeout = cfg.getLong(timeoutPath, 10L).seconds
    )
  }
}

case class ResourceTypeConfig(
  resourceType : String,
  inboundConfig : Option[InboundRouteConfig],
  outbound : List[OutboundRouteConfig],
  withCBE : Boolean,
  timeout : FiniteDuration
)

object OutboundRouteConfig {

  private[this] val outboundHeaderPath = "outboundHeader"

  def create(
    ctCtxt : ContainerContext,
    registry : BridgeProviderRegistry,
    cfg : Config,
    defaultProvider : BridgeProviderConfig,
    defaultEventProvider : BridgeProviderConfig,
    defaultLogHeader : List[String]
  ) : Try[OutboundRouteConfig] = Try {

    val id = cfg.getString("id", "default")

    val outboundHeader = cfg.getConfigList(outboundHeaderPath, List.empty).map(c => OutboundHeaderConfig.create(
      ctCtxt, registry, c, defaultProvider, defaultEventProvider, defaultLogHeader
    ).get)

    OutboundRouteConfig(
      id = id,
      outboundHeader = outboundHeader
    )
  }
}

case class OutboundRouteConfig(
  id : String,
  outboundHeader : List[OutboundHeaderConfig]
) extends WorklistItem {
  override def toString : String =
    s"${getClass().getSimpleName()}(id=$id)"
}

object InboundRouteConfig {

  private[this] val inboundUriPath = "inboundUri"
  private[this] val headerPath = "header"

  def create(
    ctCtxt : ContainerContext,
    cfg : Config
  ) : Try[InboundRouteConfig] = Try {

    val destName = ctCtxt.resolveString(cfg.getString(inboundUriPath)).map(_.toString()).get

    InboundRouteConfig(
      entry = JmsDestination.create(destName).get,
      // This will be resolved in message context
      // TODO: generalize resolver concept
      header = cfg.getStringMap(headerPath, Map.empty) // .mapValues(s => idSvc.resolvePropertyString(s).map(_.toString()).get)
    )
  }
}

case class InboundRouteConfig(
  entry : JmsDestination,
  header : Map[String, String]
)

object OutboundHeaderConfig {
  private[this] val conditionPath = "condition"
  private[this] val headerPath = "header"
  private[this] val bridgeVendorPath = "bridgeVendor"
  private[this] val bridgeProviderPath = "bridgeProvider"
  private[this] val bridgeDestinationPath = "bridgeDestination"
  private[this] val autocompletePath = "autoComplete"
  private[this] val applicationLogHeaderPath = "applicationLogHeader"
  private[this] val moduleLastOnCompletePath = "moduleLastOnComplete"
  private[this] val clearBodyPath = "clearbody"
  private[this] val maxRetryPath = "maxRetries"
  private[this] val timeToLivePath = "timeToLive"
  private[this] val deliveryModePath = "deliveryMode"

  def create(
    ctCtxt : ContainerContext,
    registry : BridgeProviderRegistry,
    cfg : Config,
    defaultProvider : BridgeProviderConfig,
    defaultEventProvider : BridgeProviderConfig,
    defaultLogHeader : List[String]
  ) : Try[OutboundHeaderConfig] = Try {

    val bridgeProvider = ProviderResolver.providerFromConfig(ctCtxt, registry, cfg, bridgeVendorPath, bridgeProviderPath).get match {
      case Some(p) => p
      case None    => defaultProvider
    }

    val bridgeDestination = cfg.getStringOption(bridgeDestinationPath).map(s => ctCtxt.resolveString(s).get).map(_.toString())
    val moduleLastOnComplete = cfg.getBoolean(moduleLastOnCompletePath, false)
    val applicationLogHeader = cfg.getStringListOption(applicationLogHeaderPath).getOrElse(defaultLogHeader)

    val clearBody = cfg.getBoolean(clearBodyPath, false)
    val autoComplete = cfg.getBoolean(autocompletePath, true)
    val maxRetries = cfg.getLong(maxRetryPath, -1L)
    val timeToLive = cfg.getDuration(timeToLivePath, 0.millis).toMillis
    val delMode = cfg.getString(deliveryModePath, JmsDeliveryMode.Persistent.asString)

    new OutboundHeaderConfig(
      bridgeProviderConfig = bridgeProvider,
      bridgeDestination = bridgeDestination,
      autoComplete = autoComplete,
      condition = cfg.getStringOption(conditionPath),
      applicationLogHeader = applicationLogHeader,
      maxRetries = maxRetries,
      timeToLive = timeToLive,
      moduleLastOnComplete = moduleLastOnComplete,
      clearBody = clearBody,
      deliveryMode = delMode,
      header = cfg.getStringMap(headerPath, Map.empty)
    )
  }
}

case class OutboundHeaderConfig(
  bridgeProviderConfig : BridgeProviderConfig,
  bridgeDestination : Option[String],
  autoComplete : Boolean,
  condition : Option[String],
  applicationLogHeader : List[String],
  maxRetries : Long,
  timeToLive : Long,
  moduleLastOnComplete : Boolean,
  clearBody : Boolean,
  deliveryMode : String,
  header : Map[String, String]
)
