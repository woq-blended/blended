package blended.updater

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.collection.immutable.Map
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions

case class BundleConfig(
  //  symbolicName: String,
  //  version: String,
  url: String,
  jarName: String,
  sha1Sum: String,
  start: Boolean,
  startLevel: Option[Int])

case class RuntimeConfig(
  name: String,
  version: String,
  framework: BundleConfig,
  bundles: Seq[BundleConfig],
  startLevel: Int,
  defaultStartLevel: Int,
  frameworkProperties: Map[String, String],
  systemProperties: Map[String, String])

object RuntimeConfig {

  def read(config: Config): RuntimeConfig = {
    val optionals = ConfigFactory.parseResources(getClass(), "RuntimeConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    val reference = ConfigFactory.parseResources(getClass(), "RuntimeConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    //    log.debug(s"Checking config with reference: ${reference}")
    config.withFallback(optionals).checkValid(reference)

    def configAsMap(key: String, default: Option[() => Map[String, String]] = None): Map[String, String] =
      if (default.isDefined && !config.hasPath(key)) {
        default.get.apply()
      } else {
        config.getConfig(key).entrySet().asScala.map {
          entry => entry.getKey() -> entry.getValue().unwrapped().asInstanceOf[String]
        }.toMap
      }

    val bundleReference = ConfigFactory.parseResources(getClass(), "BundleConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    val bundleOptionals = ConfigFactory.parseResources(getClass(), "BundleConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false))

    def readBundle(bc: Config) = BundleConfig(

      url = bc.getString("url"),
      jarName = bc.getString("jarName"),
      sha1Sum = bc.getString("sha1Sum"),
      start = if (bc.hasPath("start")) bc.getBoolean("start") else false,
      startLevel = if (bc.hasPath("startLevel")) Option(bc.getInt("startLevel")) else None
    )

    RuntimeConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      framework = readBundle(config.getConfig("framework")),
      bundles =
        if (config.hasPath("bundles"))
          config.getObjectList("bundles").asScala.map { bc => readBundle(bc.toConfig()) }.toList
        else Seq(),
      startLevel = config.getInt("startLevel"),
      defaultStartLevel = config.getInt("defaultStartLevel"),
      frameworkProperties = configAsMap("frameworkProperties", Some(() => Map())),
      systemProperties = configAsMap("systemProperties", Some(() => Map()))
    )
  }

  def toConfig(runtimeConfig: RuntimeConfig): Config = {
    def bundle(bundle: BundleConfig) = (
      Map(
        "url" -> bundle.url,
        "jarName" -> bundle.jarName,
        "sha1Sum" -> bundle.sha1Sum,
        "start" -> bundle.start
      ) ++ bundle.startLevel.map(sl => Map("start" -> sl)).getOrElse(Map())
    ).asJava

    val config = Map(
      "name" -> runtimeConfig.name,
      "version" -> runtimeConfig.version,
      "framework" -> bundle(runtimeConfig.framework),
      "bundles" -> runtimeConfig.bundles.map { b => bundle(b) }.asJava,
      "startLevel" -> runtimeConfig.startLevel,
      "defaultStartLevel" -> runtimeConfig.defaultStartLevel,
      "frameworkProperties" -> runtimeConfig.frameworkProperties.asJava,
      "systemProperties" -> runtimeConfig.systemProperties.asJava
    ).asJava

    ConfigFactory.parseMap(config)
  }

}