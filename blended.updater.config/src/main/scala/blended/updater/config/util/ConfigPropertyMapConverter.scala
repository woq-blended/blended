package blended.updater.config.util

import scala.collection.JavaConverters._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

object ConfigPropertyMapConverter {

  def unpackStringKey(key: String): String = key.replaceAll("[\"]", "")

  def getKeyAsPropertyMap(config: Config, key: String, default: Option[() => Map[String, String]] = None): Map[String, String] =
    if (default.isDefined && !config.hasPath(key)) {
      default.get.apply()
    } else {
      config.getConfig(key)
        .entrySet().asScala.map {
          entry => unpackStringKey(entry.getKey()) -> entry.getValue().unwrapped().asInstanceOf[String]
        }.toMap
    }

//  def propertyMapToConfig(map: Map[String, String]): Config = {
//    map.foldLeft(ConfigFactory.empty()) { (c, p) =>
//      c.withValue("\"" + c + "\"", ConfigValueFactory.fromAnyRef(p))
//    }
//  }

  def propertyMapToConfigValue(map: Map[String, String]): java.util.Map[String, String] = {
    map.map { case (k, v) => "\"" + k + "\"" -> v }.toMap.asJava
  }
}