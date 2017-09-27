package blended.updater.config.util

import com.typesafe.config.{Config, ConfigFactory, ConfigValue, ConfigValueFactory}

import scala.collection.JavaConverters._

object ConfigPropertyMapConverter {

  def unpackStringKey(key: String): String = key.replaceAll("[\"]", "")

  def getKeyAsPropertyMap(config: Config, key: String, default: Option[() => Map[String, String]] = None): Map[String, String] = {
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

  def propertyMapToConfigValue(m: Map[String, String]): ConfigValue = {

//    val m1 = m.map{ case (k, v) => "\"" + k + "\"" -> v }
    val result = ConfigValueFactory.fromMap(m.asJava)

    result
  }
}