package blended.util.config

import com.typesafe.config.Config
import scala.collection.JavaConverters._

trait ConfigAccessor {

  protected def configStringMap(config : Config, key: String) : Option[Map[String, String]] =
    if (config.hasPath(key)) {
      val cfg = config.getObject(key)
      Option(cfg.keySet().asScala.map { k  =>
        k -> config.getString(s"$key.$k")
      }.toMap)
    }
    else None

  protected def configConfigMap(config : Config, key: String) : Option[Map[String, Config]] =
    if (config.hasPath(key)) {
      val cfg = config.getObject(key)
      Option(cfg.keySet().asScala.map { k  =>
        k -> config.getConfig(s"$key.$k")
      }.toMap)
    }
    else None

}
