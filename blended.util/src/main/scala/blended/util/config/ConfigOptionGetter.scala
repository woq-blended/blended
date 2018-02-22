package blended.util.config

import scala.collection.JavaConverters.asScalaBufferConverter

import com.typesafe.config.Config

trait ConfigOptionGetter {

  implicit class RichOptionConfig(config: Config) {

    def getAnyRefOption(key: String): Option[AnyRef] =
      if (config.hasPath(key)) Option(config.getAnyRef(key))
      else None

    def getLongOption(key: String): Option[Long] =
      if (config.hasPath(key)) Option(config.getLong(key))
      else None

    def getStringOption(key: String): Option[String] =
      if (config.hasPath(key)) Option(config.getString(key))
      else None

    def getIntOption(key: String): Option[Int] =
      if (config.hasPath(key)) Option(config.getInt(key))
      else None

    def getBooleanOption(key: String): Option[Boolean] =
      if (config.hasPath(key)) Option(config.getBoolean(key))
      else None

    def getStringListOption(key: String): Option[List[String]] =
      if (config.hasPath(key)) Option(config.getStringList(key).asScala.toList)
      else None
  }

}
