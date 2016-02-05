package blended.updater.config

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory


case class FeatureRef(
    name: String,
    version: String,
    url: Option[String] = None) {

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},url=${url})"

}

object FeatureRef extends ((String, String, Option[String]) => FeatureRef) {

  def toConfig(feature: FeatureRef): Config = {
    val config = (Map(
      "name" -> feature.name,
      "version" -> feature.version
    ) ++
      feature.url.map(url => Map("url" -> url)).getOrElse(Map())
    ).asJava
    ConfigFactory.parseMap(config)
  }

  def fromConfig(config: Config): Try[FeatureRef] = Try {
    FeatureRef(
      name = config.getString("name"),
      version = config.getString("version"),
      url = if (config.hasPath("url")) Option(config.getString("path")) else None
    )
  }

}
