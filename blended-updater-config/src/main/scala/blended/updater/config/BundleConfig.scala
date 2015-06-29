package blended.updater.config

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class BundleConfig(
    artifact: Artifact,
    start: Boolean,
    startLevel: Option[Int]) {
  def url = artifact.url
  def jarName = artifact.fileName
  def sha1Sum = artifact.sha1Sum
}

object BundleConfig {

  def apply(url: String,
    jarName: String,
    sha1Sum: String,
    start: Boolean,
    startLevel: Option[Int]): BundleConfig =
    BundleConfig(
      artifact = Artifact(fileName = jarName, url = url, sha1Sum = sha1Sum),
      start = start,
      startLevel = startLevel
    )

  def read(config: Config): Try[BundleConfig] = Try {
    BundleConfig(
      url = config.getString("url"),
      jarName = config.getString("jarName"),
      sha1Sum = config.getString("sha1Sum"),
      start = if (config.hasPath("start")) config.getBoolean("start") else false,
      startLevel = if (config.hasPath("startLevel")) Option(config.getInt("startLevel")) else None
    )
  }

  def toConfig(bundleConfig: BundleConfig): Config = {
    val config = (Map(
      "url" -> bundleConfig.url,
      "jarName" -> bundleConfig.jarName,
      "sha1Sum" -> bundleConfig.sha1Sum,
      "start" -> bundleConfig.start
    ) ++ bundleConfig.startLevel.map(sl => Map("startLevel" -> sl)).getOrElse(Map())
    ).asJava
    ConfigFactory.parseMap(config)
  }
}
