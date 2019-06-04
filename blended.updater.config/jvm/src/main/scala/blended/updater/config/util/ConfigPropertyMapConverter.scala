package blended.updater.config.util

import com.typesafe.config.{Config, ConfigValue, ConfigValueFactory}

import scala.collection.JavaConverters._

// TODO: move to blended.util project
object ConfigPropertyMapConverter {

  def unpackStringKey(key : String) : String = key.replaceAll("[\"]", "")

  def getKeyAsPropertyMap(config : Config, key : String, default : Option[() => Map[String, String]] = None) : Map[String, String] = {
    val result =
      if (default.isDefined && !config.hasPath(key)) {
        default.get.apply()
      } else {
        val fromCfg = config.getConfig(key).entrySet().asScala

        fromCfg.map {
          entry => unpackStringKey(entry.getKey()) -> entry.getValue().unwrapped().asInstanceOf[String]
        }.toMap
      }

    result
  }

  def propertyMapToConfigValue(m : Map[String, String]) : ConfigValue = {
    ConfigValueFactory.fromMap(m.asJava)
  }
}
