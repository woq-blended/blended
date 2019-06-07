package blended.mgmt.service.jmx.internal

import com.typesafe.config.{Config, ConfigObject}

import scala.collection.JavaConverters._
import blended.util.config.Implicits._

object ServiceJmxConfig {

  private val intervalPath = "interval"
  private val templatesPath = "templates"
  private val servicesPath = "services"

  private val defaultInterval : Int = 5

  private def readTemplates(cfg : ConfigObject) : Map[String, ServiceTypeTemplate] = {
    cfg.unwrapped().asScala.map {
      case (key, _) =>
        (key, ServiceTypeTemplate(key, cfg.toConfig().getObject(key).toConfig()))
    }.toMap
  }

  private def readServices(cfg : ConfigObject) : Map[String, SingleServiceConfig] = {
    cfg.unwrapped().asScala.map {
      case (key, _) =>
        (key, SingleServiceConfig(key, cfg.toConfig().getObject(key).toConfig()))
    }.toMap
  }

  def getStringMap(cfg : ConfigObject) : Map[String, String] =
    cfg.unwrapped().asScala.map { case (key, _) => (key, cfg.toConfig().getString(key)) }.toMap

  def apply(cfg : Config) : ServiceJmxConfig = new ServiceJmxConfig(

    interval = cfg.getInt(intervalPath, defaultInterval),

    templates = if (cfg.hasPath(templatesPath)) {
      readTemplates(cfg.getObject(templatesPath))
    } else {
      Map.empty
    },

    services = if (cfg.hasPath(servicesPath)) {
      readServices(cfg.getObject(servicesPath))
    } else {
      Map.empty
    }
  )
}

case class ServiceJmxConfig(
  interval : Int,
  templates : Map[String, ServiceTypeTemplate],
  services : Map[String, SingleServiceConfig]
)

object ServiceTypeTemplate {

  val queryPath = "query"
  val attributesPath = "attributes"

  def apply(svcName : String, cfg : Config) : ServiceTypeTemplate = new ServiceTypeTemplate(
    name = svcName,
    domain = cfg.getString("domain"),
    query = if (cfg.hasPath(queryPath)) ServiceJmxConfig.getStringMap(cfg.getObject(queryPath)) else Map.empty,
    attributes = if (cfg.hasPath(attributesPath)) cfg.getStringList(attributesPath).asScala.toList else List.empty
  )
}

case class ServiceTypeTemplate(
  name : String,
  domain : String,
  query : Map[String, String],
  attributes : List[String]
)

case object SingleServiceConfig {

  val queryPath = "query"
  val svcTypePath = "serviceType"
  val attributesPath = "attributes"

  def apply(svcName : String, cfg : Config) : SingleServiceConfig = new SingleServiceConfig(
    name = svcName,
    svcType = cfg.getString(svcTypePath),
    query = if (cfg.hasPath(queryPath)) ServiceJmxConfig.getStringMap(cfg.getObject(queryPath)) else Map.empty,
    attributes = if (cfg.hasPath(attributesPath)) cfg.getStringList(attributesPath).asScala.toList else List.empty
  )
}

case class SingleServiceConfig(
  name : String,
  svcType : String,
  query : Map[String, String],
  attributes : List[String]
)
