package blended.updater.config

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions

case class BundleConfig(
    artifact: Artifact,
    start: Boolean,
    startLevel: Option[Int]) {
  def url: String = artifact.url
  def jarName: Option[String] = artifact.fileName
  def sha1Sum: Option[String] = artifact.sha1Sum
}

object BundleConfig {

  def apply(url: String,
    jarName: String = null,
    sha1Sum: String = null,
    start: Boolean,
    startLevel: Integer = null): BundleConfig =
    BundleConfig(
      artifact = Artifact(fileName = Option(jarName), url = url, sha1Sum = Option(sha1Sum)),
      start = start,
      startLevel = Option(startLevel)
    )

  def read(config: Config): Try[BundleConfig] = Try {
    val reference = ConfigFactory.parseResources(getClass(), "RuntimeConfig.BundleConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    val optionals = ConfigFactory.parseResources(getClass(), "RuntimeConfig.BundleConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    config.withFallback(optionals).checkValid(reference)

    BundleConfig(
      artifact = Artifact(
        url = config.getString("url"),
        fileName = if (config.hasPath("jarName")) Option(config.getString("jarName")) else None,
        sha1Sum = if (config.hasPath("sha1Sum")) Option(config.getString("sha1Sum")) else None
      ),
      start = if (config.hasPath("start")) config.getBoolean("start") else false,
      startLevel = if (config.hasPath("startLevel")) Option(config.getInt("startLevel")) else None
    )
  }

  def toConfig(bundleConfig: BundleConfig): Config = {
    val config = (
      Map(
        "url" -> bundleConfig.url,
        "start" -> bundleConfig.start
      ) ++
        bundleConfig.jarName.map(n => Map("jarName" -> n)).getOrElse(Map()) ++
        bundleConfig.sha1Sum.map(n => Map("sha1Sum" -> n)).getOrElse(Map()) ++
        bundleConfig.startLevel.map(sl => Map("startLevel" -> sl)).getOrElse(Map())
    ).asJava
    ConfigFactory.parseMap(config)
  }
}
