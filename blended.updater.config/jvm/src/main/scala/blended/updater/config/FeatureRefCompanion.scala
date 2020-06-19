package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try

object FeatureRefCompanion {

  def toConfig(feature : FeatureRef) : Config = {
    val config = (Map(
      "name" -> feature.name,
      "version" -> feature.version
    ) ++
      feature.url.map(url => Map("url" -> url)).getOrElse(Map())).asJava
    ConfigFactory.parseMap(config)
  }

  def fromConfig(config : Config) : Try[FeatureRef] = Try {
    FeatureRef(
      name = config.getString("name"),
      version = config.getString("version"),
      url = if (config.hasPath("url")) Option(config.getString("url")) else None
    )
  }
}
