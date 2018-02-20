package blended.util.config

import scala.collection.JavaConverters.asScalaBufferConverter

import com.typesafe.config.Config

trait ConfigDefaultGetter {

  implicit class RichConfig(config: Config) {

    def getLong(key: String, default: Long): Long =
      if (config.hasPath(key)) config.getLong(key)
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

    // Read the bundle config value for the given `key` or return the default, when the key is not present
    def getOrDefault[T](key: String, default: T): T = if (config.hasPath(key)) {
      default match {
        case x: String => config.getString(key).asInstanceOf[T]
        case x: Int => config.getInt(key).asInstanceOf[T]
        case x: java.lang.Integer => config.getInt(key).asInstanceOf[T]
        case x: Long => config.getLong(key).asInstanceOf[T]
        case x: java.lang.Long => config.getLong(key).asInstanceOf[T]
        case x: Boolean => config.getBoolean(key).asInstanceOf[T]
        case x: java.lang.Boolean => config.getBoolean(key).asInstanceOf[T]
      }
    } else default

  }

}
