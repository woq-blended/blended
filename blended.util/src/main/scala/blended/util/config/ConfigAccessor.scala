package blended.util.config

import com.typesafe.config.Config

import scala.jdk.CollectionConverters._

trait ConfigAccessor {

  protected def configStringMap(config : Config, key : String) : Option[Map[String, String]] =
    if (config.hasPath(key)) {
      val cfg = config.getObject(key)
      Option(cfg.keySet().asScala.map { k =>
        k -> config.getString(s"$key.$k")
      }.toMap)
    } else {
      None
    }

  protected def configConfigMap(config : Config, key : String) : Option[Map[String, Config]] =
    if (config.hasPath(key)) {
      val cfg = config.getObject(key)
      val cfgC = cfg.toConfig()
      Option(cfg.keySet().asScala.map { k =>
        k -> cfgC.getConfig(k)
      }.toMap)
    } else {
      None
    }

  protected def configConfigList(config : Config, key : String) : Option[List[Config]] =
    if (config.hasPath(key)) {
      Some(config.getConfigList(key).asScala.toList)
    } else {
      None
    }
}
