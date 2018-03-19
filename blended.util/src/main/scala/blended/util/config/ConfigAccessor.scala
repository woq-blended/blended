package blended.util.config

import com.typesafe.config.Config
import scala.collection.JavaConverters._

trait ConfigAccessor {

  protected def configStringMap(config : Config, key: String) : Option[Map[String, String]] =
    if (config.hasPath(key)) {
      val cfg = config.getConfig(key)
      Option(cfg.entrySet().asScala.map { entry  =>
        entry.getKey -> cfg.getString(entry.getKey)
      }.toMap)
    }
    else None

  protected def configConfigMap(config : Config, key: String) : Option[Map[String, Config]] =
    if (config.hasPath(key)) {
      val cfg = config.getConfig(key)
      Option(cfg.entrySet().asScala.map { entry  =>
        entry.getKey -> cfg.getConfig(entry.getKey)
      }.toMap)
    }
    else None

}
