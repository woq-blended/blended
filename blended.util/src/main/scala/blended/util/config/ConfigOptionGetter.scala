package blended.util.config

import com.typesafe.config.Config

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.duration._

trait ConfigOptionGetter extends ConfigAccessor {

  implicit class RichOptionConfig(config : Config) {

    def getAnyRefOption(key : String) : Option[AnyRef] =
      if (config.hasPath(key)) {
        Option(config.getAnyRef(key))
      } else {
        None
      }

    def getLongOption(key : String) : Option[Long] =
      if (config.hasPath(key)) {
        Option(config.getLong(key))
      } else {
        None
      }

    def getStringOption(key : String) : Option[String] =
      if (config.hasPath(key)) {
        Option(config.getString(key))
      } else {
        None
      }

    def getIntOption(key : String) : Option[Int] =
      if (config.hasPath(key)) {
        Option(config.getInt(key))
      } else {
        None
      }

    def getBooleanOption(key : String) : Option[Boolean] =
      if (config.hasPath(key)) {
        Option(config.getBoolean(key))
      } else {
        None
      }

    def getDurationOption(key : String) : Option[FiniteDuration] =
      if (config.hasPath(key)) {
        Option(config.getDuration(key).toMillis.millis)
      } else {
        None
      }

    def getStringListOption(key : String) : Option[List[String]] =
      if (config.hasPath(key)) {
        Option(config.getStringList(key).asScala.toList)
      } else {
        None
      }

    def getStringMapOption(key : String) : Option[Map[String, String]] = configStringMap(config, key)

    def getConfigMapOption(key : String) : Option[Map[String, Config]] = configConfigMap(config, key)

    def getConfigOption(key : String) : Option[Config] =
      if (config.hasPath(key)) {
        Option(config.getConfig(key))
      } else {
        None
      }
  }
}
