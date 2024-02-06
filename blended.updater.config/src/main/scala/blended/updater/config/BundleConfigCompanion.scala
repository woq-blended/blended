package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try

object BundleConfigCompanion {

  def read(config : Config) : Try[BundleConfig] = Try {

    BundleConfig(
      artifact = if (config.hasPath("artifact")) {
        // read artifact
        ArtifactCompanion.read(config.getConfig("artifact")).get
      } else {
        // read legacy-structure directly
        Artifact(
          url = config.getString("url"),
          fileName = if (config.hasPath("jarName")) Option(config.getString("jarName")) else None,
          sha1Sum = if (config.hasPath("sha1Sum")) Option(config.getString("sha1Sum")) else None
        )
      },
      start = if (config.hasPath("start")) config.getBoolean("start") else false,
      startLevel = if (config.hasPath("startLevel")) Option(config.getInt("startLevel")) else None
    )
  }

  def toConfig(bundleConfig : BundleConfig) : Config = {
    val config : java.util.Map[String, Any] = (
      Map("url" -> bundleConfig.url) ++
      (if (bundleConfig.start) Map("start" -> bundleConfig.start) else Map.empty[String, Any]) ++
      bundleConfig.jarName.map(n => Map("jarName" -> n)).getOrElse(Map.empty[String, Any]) ++
      bundleConfig.sha1Sum.map(n => Map("sha1Sum" -> n)).getOrElse(Map.empty[String, Any]) ++
      bundleConfig.startLevel.map(sl => Map("startLevel" -> sl)).getOrElse(Map.empty[String, Any])
    ).asJava

    ConfigFactory.parseMap(config)
  }
}
