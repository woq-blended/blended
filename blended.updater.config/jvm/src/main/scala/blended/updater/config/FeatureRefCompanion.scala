package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try

object FeatureRefCompanion {

  def toConfig(feature : FeatureRef) : Config = {

    val config : java.util.Map[String, String] = Map(
      "url" -> feature.url,
      "names" -> feature.names.map(s => "\"" + s + "\"").mkString("[", ",", "]")
    ).asJava

    val cfg : Config = ConfigFactory.parseMap(config)
    println(cfg)
    cfg
  }

  def fromConfig(config : Config) : Try[FeatureRef] = Try {
    FeatureRef(
      url = config.getString("url"),
      names = config.getStringList("names").asScala.toList
    )
  }
}
