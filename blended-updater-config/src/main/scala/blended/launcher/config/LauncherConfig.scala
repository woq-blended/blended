package blended.launcher.config

import java.io.File
import scala.collection.JavaConverters._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions

object LauncherConfig {

  case class BundleConfig(location: String, start: Boolean = false, startLevel: Int)

  private[this] val ConfigPrefix = "blended.launcher.Launcher"

  /**
   * Read and validate the given config object.
   * @return A valid [LauncherConfig] read from the given config.
   */
  def read(config: Config): LauncherConfig = {

    val optionals = ConfigFactory.parseResources(getClass(), "LauncherConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    val reference = ConfigFactory.parseResources(getClass(), "LauncherConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    config.withFallback(optionals).checkValid(reference)

    def configAsMap(key: String, default: Option[() => Map[String, String]] = None): Map[String, String] =
      if (default.isDefined && !config.hasPath(key)) {
        default.get.apply()
      } else {
        config.getConfig(key)
          .entrySet().asScala.map {
            entry => entry.getKey() -> entry.getValue().unwrapped().asInstanceOf[String]
          }.toMap
      }

    LauncherConfig(
      frameworkJar = config.getString("frameworkBundle"),
      systemProperties = configAsMap("systemProperties", Some(() => Map())),
      frameworkProperties = configAsMap("frameworkProperties", Some(() => Map())),
      startLevel = config.getInt("startLevel"),
      defaultStartLevel = config.getInt("defaultStartLevel"),
      bundles = config.getObjectList("bundles")
        .asScala.map { b =>
          val c = b.toConfig()
          BundleConfig(
            location = c.getString("location"),
            start = if (c.hasPath("start")) c.getBoolean("start") else false,
            startLevel = if (c.hasPath("startLevel")) c.getInt("startLevel") else config.getInt("defaultStartLevel")
          )
        }.toList,
      branding = configAsMap("branding", Some(() => Map()))
    )

  }

  /**
   * Read and validate the given file.
   * @return A valid [LauncherConfig].
   */
  def read(file: File): LauncherConfig = {
    val config = ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).getConfig(ConfigPrefix).resolve()
    read(config)
  }

  def toConfig(launcherConfig: LauncherConfig): Config = {
    val config = Map(
      "frameworkBundle" -> launcherConfig.frameworkJar,
      "systemProperties" -> launcherConfig.systemProperties.asJava,
      "frameworkProperties" -> launcherConfig.frameworkProperties.asJava,
      "startLevel" -> launcherConfig.startLevel,
      "defaultStartLevel" -> launcherConfig.defaultStartLevel,
      "bundles" -> launcherConfig.bundles.map { b =>
        Map(
          "location" -> b.location,
          "start" -> b.start,
          "startLevel" -> b.startLevel
        ).asJava
      }.asJava,
      "branding" -> launcherConfig.branding.asJava
    ).asJava
    ConfigFactory.parseMap(config)
  }

}

case class LauncherConfig(
    frameworkJar: String,
    systemProperties: Map[String, String],
    frameworkProperties: Map[String, String],
    startLevel: Int,
    defaultStartLevel: Int,
    bundles: Seq[LauncherConfig.BundleConfig],
    branding: Map[String, String]) {

  override def toString = s"${getClass().getSimpleName()}(frameworkJar=${frameworkJar},systemProperties=${systemProperties}" +
    s",frameworkProperties=${frameworkProperties},startLevel=${startLevel},defaultStartLevel=${defaultStartLevel}" +
    s",bundles=${bundles},branding=${branding})"
}

