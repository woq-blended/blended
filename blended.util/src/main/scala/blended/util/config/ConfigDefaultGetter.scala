package blended.util.config

import scala.collection.JavaConverters.asScalaBufferConverter

import com.typesafe.config.Config

trait ConfigDefaultGetter {

  implicit class RichDefaultConfig(config: Config) {

    def getLong(key: String, default: Long): Long =
      if (config.hasPath(key)) config.getLong(key)
      else default

    def getAnyRef(key: String, default: AnyRef): AnyRef =
      if (config.hasPath(key)) config.getAnyRef(key)
      else default

    def getString(key: String, default: String): String =
      if (config.hasPath(key)) config.getString(key)
      else default

    def getInt(key: String, default: Int): Int =
      if (config.hasPath(key)) config.getInt(key)
      else default

    def getBoolean(key: String, default: Boolean): Boolean =
      if (config.hasPath(key)) config.getBoolean(key)
      else default

    def getStringList(key: String, default: List[String]): List[String] =
      if (config.hasPath(key)) config.getStringList(key).asScala.toList
      else default

  }

}
