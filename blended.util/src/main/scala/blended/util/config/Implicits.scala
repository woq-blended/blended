package blended.util.config

import com.typesafe.config.Config

trait ConfigDefaultGetter {

  implicit class RichConfig(config: Config) {

    def getLong(key: String, default: Long): Long = getOrDefault(key, default)
    def getString(key: String, default: String): String = getOrDefault(key, default)
    def getInt(key: String, default: Int): Int = getOrDefault(key, default)
    def getBoolean(key: String, default: Boolean): Boolean = getOrDefault(key, default)

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

object Implicits extends ConfigDefaultGetter 