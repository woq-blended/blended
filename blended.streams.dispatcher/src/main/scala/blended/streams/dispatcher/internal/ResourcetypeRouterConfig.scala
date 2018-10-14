package blended.streams.dispatcher.internal

import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.JmsDestination
import blended.streams.jms.JmsDeliveryMode
import blended.util.config.Implicits._
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.util.Try

object ResourceTypeRouterConfig {

  private[this] val defaultEventVendorPath = "defaultEventVendor"
  private[this] val defaultEventProviderPath = "defaultEventProvider"
  private[this] val applicationLogHeaderPath = "applicationLogHeader"
  private[this] val defaultHeaderPath = "defaultHeader"
  private[this] val resourcetypesPath = "resourcetypes"

  private[dispatcher] def resolveProvider(
    idSvc : ContainerIdentifierService,
    registry : BridgeProviderRegistry,
    cfg : Config,
    vendorPath : String,
    providerPath : String
  ) : Try[Option[BridgeProviderConfig]] = Try {

    (cfg.getStringOption(vendorPath), cfg.getStringOption(providerPath)) match {
      case (Some(v), Some(p)) =>
        Some(getProvider(
          registry,
          idSvc.resolvePropertyString(v).map(_.toString()).get,
          idSvc.resolvePropertyString(p).map(_.toString()).get
        ).get)
      case (_,_) =>
        None
    }
  }

  private[dispatcher] def getProvider(
    registry : BridgeProviderRegistry,
    vendor : String,
    provider : String
  ) : Try[BridgeProviderConfig] = Try {
    registry.jmsProvider(vendor, provider) match {
      case None => throw new Exception(s"Event provider [$vendor:$provider] is not configured in provider registry.")
      case Some(p) => p
    }
  }

  def create(
    idSvc : ContainerIdentifierService,
    provider: BridgeProviderRegistry,
    cfg: Config
  ) : Try[ResourceTypeRouterConfig] = Try {

    val internalProvider = provider.internalProvider.get

    val eventProvider = getProvider(
      provider,
      cfg.getString(defaultEventVendorPath, internalProvider.vendor),
      cfg.getString(defaultEventProviderPath, internalProvider.provider)
    ).get

    val routes : Map[String, ResourceTypeConfig] =
      cfg.getConfigMap(resourcetypesPath, Map.empty).map { case (key, value) =>
        key -> ResourceTypeConfig.create(idSvc, provider, key, internalProvider, eventProvider, value).get
      }

    val logHeader : List[String] = cfg.getStringList(applicationLogHeaderPath, List.empty)

    val defaultHeader : List[DefaultHeaderConfig] = cfg.getConfigList(defaultHeaderPath, List.empty).map{ cfg =>
      DefaultHeaderConfig.create(idSvc, cfg)
    }

    ResourceTypeRouterConfig(
      defaultProvider = internalProvider,
      defaultEventProvider = eventProvider,
      applicationLogHeader = logHeader,
      defaultHeader = defaultHeader,
      resourceTypeConfigs = routes
    )
  }
}

object DefaultHeaderConfig {

  def create(idSvc : ContainerIdentifierService, cfg : Config) : DefaultHeaderConfig = {

    val name = cfg.getString("name")
    val expr = cfg.getStringOption("expression")
    val overwrite = cfg.getBoolean("overwrite", true)

    DefaultHeaderConfig(name, expr, overwrite)
  }
}

case class DefaultHeaderConfig(
  name : String,
  value : Option[String],
  overwrite : Boolean
)

case class ResourceTypeRouterConfig(
  defaultProvider : BridgeProviderConfig,
  defaultEventProvider : BridgeProviderConfig,
  applicationLogHeader : List[String],
  defaultHeader : List[DefaultHeaderConfig],
  resourceTypeConfigs : Map[String, ResourceTypeConfig]
)

object ResourceTypeConfig {

  private[this] val inboundPath = "inbound"
  private[this] val cbePath = "withCBE"
  private[this] val outboundPath = "outbound"

  def create (
    idSvc : ContainerIdentifierService,
    registry: BridgeProviderRegistry,
    resType: String,
    defaultProvider: BridgeProviderConfig,
    defaultEventProvider: BridgeProviderConfig,
    cfg: Config
  ) : Try[ResourceTypeConfig] = Try {

    val outboundRoutes : List[OutboundRouteConfig] = cfg.getConfigList(outboundPath).asScala.map { c =>
      OutboundRouteConfig.create(idSvc, registry, c, defaultProvider, defaultEventProvider).get
    }.toList

    ResourceTypeConfig(
      resourceType = resType,
      withCBE = cfg.getBoolean("withCBE", true),
      outbound = outboundRoutes,
      inboundConfig = cfg.getConfigOption(inboundPath).map(c => InboundRouteConfig.create(idSvc, c).get)
    )
  }
}

case class ResourceTypeConfig(
  resourceType: String,
  inboundConfig : Option[InboundRouteConfig],
  outbound : List[OutboundRouteConfig],
  withCBE: Boolean
)

object OutboundRouteConfig {

  private[this] val applicationLogHeaderPath = "applicationLogHeader"
  private[this] val bridgeVendorPath = "bridgeVendor"
  private[this] val bridgeProviderPath = "bridgeProvider"
  private[this] val bridgeDestinationPath = "bridgeDestination"
  private[this] val moduleLastOnCompletePath = "moduleLastOnComplete"
  private[this] val outboundHeaderPath = "outboundHeader"
  private[this] val clearBodyPath = "clearbody"
  private[this] val autocompletePath = "autoComplete"
  private[this] val maxRetryPath = "maxRetries"
  private[this] val timeToLivePath = "timeToLive"
  private[this] val eventVendorPath = "eventVendor"
  private[this] val eventProviderPath = "eventProvider"
  private[this] val eventDestinationPath = "eventDestination"
  private[this] val deliveryModePath = "deliveryMode"

  def create(
    idSvc : ContainerIdentifierService,
    registry: BridgeProviderRegistry,
    cfg: Config,
    defaultProvider: BridgeProviderConfig,
    defaultEventProvider: BridgeProviderConfig
  ) : Try[OutboundRouteConfig] = Try {

    val id = cfg.getString("id", "default")

    val bridgeProvider = ResourceTypeRouterConfig.resolveProvider(idSvc, registry, cfg, bridgeVendorPath, bridgeProviderPath).get match {
      case Some(p) => p
      case None => defaultProvider
    }

    val eventProvider = ResourceTypeRouterConfig.resolveProvider(idSvc, registry, cfg, eventVendorPath, eventProviderPath).get match {
      case Some(p) => p
      case None => defaultEventProvider
    }

    val bridgeDestination = cfg.getStringOption(bridgeDestinationPath).map(s => JmsDestination.create(idSvc.resolvePropertyString(s).map(_.toString()).get).get)
    val moduleLastOnComplete = cfg.getBoolean(moduleLastOnCompletePath, false)
    val applicationLogHeader = cfg.getStringListOption(applicationLogHeaderPath).getOrElse(List.empty)
    val outboundHeader  = cfg.getConfigList(outboundHeaderPath, List.empty).map(c => OutboundHeaderConfig.create(idSvc, c).get)
    val clearBody = cfg.getBoolean(clearBodyPath, false)
    val autoComplete = cfg.getBoolean(autocompletePath, true)
    val maxRetries = cfg.getLong(maxRetryPath, -1L)
    val timeToLive = cfg.getLong(timeToLivePath, 0L)
    val delMode = cfg.getString(deliveryModePath, JmsDeliveryMode.Persistent.asString)

    OutboundRouteConfig(
      id = id,
      bridgeProvider = bridgeProvider,
      bridgeDestination = bridgeDestination,
      eventProvider = eventProvider,
      applicationLogHeader = applicationLogHeader,
      outboundHeader = outboundHeader,
      maxRetries = maxRetries,
      timeToLive = timeToLive,
      moduleLastOnComplete = moduleLastOnComplete,
      clearBody = clearBody,
      autoComplete = autoComplete,
      deliveryMode = delMode
    )
  }
}

case class OutboundRouteConfig(
  id : String,
  bridgeProvider: BridgeProviderConfig,
  bridgeDestination: Option[JmsDestination],
  eventProvider : BridgeProviderConfig,
  applicationLogHeader : List[String],
  outboundHeader: List[OutboundHeaderConfig],
  deliveryMode : String,
  maxRetries: Long,
  timeToLive : Long,
  moduleLastOnComplete: Boolean,
  clearBody : Boolean,
  autoComplete : Boolean
) {
  override def toString: String =
    s"${getClass().getSimpleName()}(id=$id, bridgeProvider=$bridgeProvider, " +
    s"bridgeDestination=$bridgeDestination, eventProvider=$eventProvider)"
}

object InboundRouteConfig {

  private[this] val inboundUriPath = "inboundUri"
  private[this] val headerPath = "header"

  def create(
    idSvc: ContainerIdentifierService,
    cfg: Config
  ) : Try[InboundRouteConfig] = Try {

    val destName = idSvc.resolvePropertyString(cfg.getString(inboundUriPath)).map(_.toString()).get

    InboundRouteConfig(
      entry = JmsDestination.create(destName).get,
      header = cfg.getStringMap(headerPath, Map.empty).mapValues(s => idSvc.resolvePropertyString(s).map(_.toString()).get)
    )
  }
}

case class InboundRouteConfig(
  entry: JmsDestination,
  header : Map[String, String]
)

object OutboundHeaderConfig {
  private[this] val conditionPath = "condition"
  private[this] val headerPath = "header"

  def create (idSvc: ContainerIdentifierService, cfg: Config) : Try[OutboundHeaderConfig] = Try {
    new OutboundHeaderConfig(
      condition = cfg.getStringOption(conditionPath).map(s => idSvc.resolvePropertyString(s).map(_.toString()).get),
      header = cfg.getStringMap(headerPath, Map.empty).mapValues(s => idSvc.resolvePropertyString(s).map(_.toString()).get)
    )
  }
}

case class OutboundHeaderConfig(
  condition : Option[String],
  header : Map[String, String]
)
