package blended.streams.dispatcher.internal

import com.typesafe.config.Config

import scala.collection.JavaConverters._

object ResourceTypeRouterConfig {

  private[this] val defaultVendorPath = "defaultVendor"
  private[this] val defaultProviderPath = "defaultProvider"
  private[this] val defaultEventVendorPath = "defaultEventVendor"
  private[this] val defaultEventProviderPath = "defaultEventProvider"
  private[this] val inboundUriPath  = "inboundUri"
  private[this] val outboundUriPath  = "outboundUri"
  private[this] val errorUriPath  = "errorUri"
  private[this] val applicationLogHeaderPath = "applicationLogHeader"
  private[this] val resourcetypesPath = "resourcetypes"

  def apply(cfg: Config) : ResourceTypeRouterConfig = {

    val vendor = cfg.getString(defaultVendorPath)
    val provider = cfg.getString(defaultProviderPath)

    val evtVendor = if (cfg.hasPath(defaultEventVendorPath)) cfg.getString(defaultEventVendorPath) else vendor
    val evtProvider = if (cfg.hasPath(defaultEventProviderPath)) cfg.getString(defaultEventProviderPath) else provider

    val rTypes = cfg.getObject(resourcetypesPath)

    val routes = rTypes.keySet().asScala.map { key =>
      val rtCfg = cfg.getConfig(s"$resourcetypesPath.$key")
      (key, ResourceTypeConfig(key, vendor, provider, evtVendor, evtProvider, rtCfg))
    }.toMap

    val logHeader : List[String] = cfg.hasPath(applicationLogHeaderPath) match {
      case true => cfg.getStringList(applicationLogHeaderPath).asScala.toList
      case _ => List.empty
    }

    new ResourceTypeRouterConfig(
      defaultVendor = vendor,
      defaultProvider = provider,
      defaultEventVendor = evtVendor,
      defaultEventProvider = evtProvider,
      inboundUri = cfg.getString(inboundUriPath),
      outboundUri = cfg.getString(outboundUriPath),
      errorUri = cfg.getString(errorUriPath),
      applicationLogHeader = logHeader,
      resourceTypeConfigs = routes
    )
  }

  def getConfigStringMap(cfg: Config) : Map[String, String] = cfg.entrySet().asScala.map { entry  =>
    entry.getKey -> cfg.getString(entry.getKey)
  }.toMap
}

case class ResourceTypeRouterConfig(
  defaultVendor : String,
  defaultProvider: String,
  defaultEventVendor : String,
  defaultEventProvider : String,
  inboundUri : String,
  outboundUri : String,
  errorUri : String,
  applicationLogHeader : List[String],
  resourceTypeConfigs : Map[String, ResourceTypeConfig]
)

object ResourceTypeConfig {

  private[this] val inboundPath = "inbound"
  private[this] val cbePath = "withCBE"
  private[this] val outboundPath = "outbound"

  def apply(
    resType: String,
    defaultVendor : String,
    defaultProvider: String,
    defaultEventVendor : String,
    defaultEventProvider: String,
    cfg: Config
  ) : ResourceTypeConfig = {

    val outboundRoutes : List[OutboundRouteConfig] = cfg.getConfigList(outboundPath).asScala.map { c =>
      OutboundRouteConfig(c, defaultVendor, defaultProvider, defaultEventVendor, defaultEventProvider)
    }.toList

    ResourceTypeConfig(
      resourceType = resType,
      withCBE = !cfg.hasPath(cbePath) || cfg.getBoolean(cbePath),
      outbound = outboundRoutes,
      inboundConfig = cfg.hasPath(inboundPath) match {
        case true => Some(InboundRouteConfig(cfg.getConfig(inboundPath)))
        case _ => None
      }
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

  def apply(
    cfg: Config,
    defaultVendor: String,
    defaultProvider: String,
    defaultEventVendor : String,
    defaultEventProvider: String
  ) : OutboundRouteConfig = OutboundRouteConfig(

    id = if (cfg.hasPath("id")) cfg.getString("id") else "default",

    bridgeVendor = cfg.hasPath(bridgeVendorPath) match {
      case false => defaultVendor
      case _ => cfg.getString(bridgeVendorPath)
    },

    bridgeProvider = cfg.hasPath(bridgeProviderPath) match {
      case false => defaultProvider
      case _ => cfg.getString(bridgeProviderPath)
    },

    bridgeDestination = cfg.hasPath(bridgeDestinationPath) match {
      case false => None
      case _ => Some(cfg.getString(bridgeDestinationPath))
    },

    eventVendor = if (cfg.hasPath(eventVendorPath)) cfg.getString(eventVendorPath) else defaultEventVendor,

    eventProvider = if (cfg.hasPath(eventProviderPath)) cfg.getString(eventProviderPath) else defaultEventProvider,

    moduleLastOnComplete = cfg.hasPath(moduleLastOnCompletePath) && cfg.getBoolean(moduleLastOnCompletePath),


    applicationLogHeader = cfg.hasPath(applicationLogHeaderPath) match {
      case false => List.empty
      case _ => cfg.getStringList(applicationLogHeaderPath).asScala.toList
    },

    outboundHeader  = cfg.hasPath(outboundHeaderPath) match {
      case true =>
        cfg.getConfigList(outboundHeaderPath).asScala.map { cfg =>
          OutboundHeaderConfig(cfg)
        }.toList
      case _ => List.empty
    },

    clearBody = cfg.hasPath(clearBodyPath) && cfg.getBoolean(clearBodyPath),

    autoComplete = !cfg.hasPath(autocompletePath) || cfg.getBoolean(autocompletePath),

    maxRetries = cfg.hasPath(maxRetryPath) match {
      case false => -1l
      case true => cfg.getLong(maxRetryPath)
    },

    timeToLive = cfg.hasPath(timeToLivePath) match {
      case false => 0l
      case true => cfg.getLong(timeToLivePath)
    }
  )
}

case class OutboundRouteConfig(
  id : String,
  bridgeVendor : String,
  bridgeProvider: String,
  bridgeDestination: Option[String],
  eventVendor: String,
  eventProvider : String,
  applicationLogHeader : List[String],
  outboundHeader: List[OutboundHeaderConfig],
  maxRetries: Long,
  timeToLive : Long,
  moduleLastOnComplete: Boolean,
  clearBody : Boolean,
  autoComplete : Boolean
) {
  override def toString: String =
    s"${getClass().getSimpleName()}(id=$id, bridgeVendor=$bridgeVendor, bridgeProvider=$bridgeProvider, " +
    s"bridgeDestination=$bridgeDestination, eventVendor=$eventVendor, eventProvider=$eventProvider)"
}

object InboundRouteConfig {

  private[this] val inboundUriPath = "inboundUri"
  private[this] val headerPath = "header"

  def apply(cfg: Config) : InboundRouteConfig = {

    InboundRouteConfig(
      entryUri = cfg.getString(inboundUriPath),

      header = cfg.hasPath(headerPath) match {
        case false => Map.empty
        case true => ResourceTypeRouterConfig.getConfigStringMap(cfg.getConfig(headerPath))
      }
    )
  }
}

case class InboundRouteConfig(entryUri: String, header : Map[String, String])

object OutboundHeaderConfig {
  private[this] val conditionPath = "condition"
  private[this] val headerPath = "header"

  def apply(cfg: Config) : OutboundHeaderConfig = {
    new OutboundHeaderConfig(
      condition = cfg.hasPath(conditionPath) match {
        case true => Some(cfg.getString(conditionPath))
        case _ => None
      },
      header = ResourceTypeRouterConfig.getConfigStringMap(cfg.getConfig(headerPath))
    )
  }
}

case class OutboundHeaderConfig(
  condition : Option[String],
  header : Map[String, String]
)
