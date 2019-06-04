package blended.launcher.config

import java.io.File

import com.typesafe.config._

import scala.collection.JavaConverters._
import blended.updater.config.util.ConfigPropertyMapConverter

case class LauncherConfig(
  frameworkJar : String,
  systemProperties : Map[String, String],
  frameworkProperties : Map[String, String],
  startLevel : Int,
  defaultStartLevel : Int,
  bundles : Seq[LauncherConfig.BundleConfig],
  branding : Map[String, String]
) {

  override def toString = s"${getClass().getSimpleName()}(frameworkJar=${frameworkJar},systemProperties=${systemProperties}" +
    s",frameworkProperties=${frameworkProperties},startLevel=${startLevel},defaultStartLevel=${defaultStartLevel}" +
    s",bundles=${bundles},branding=${branding})"
}

object LauncherConfig {

  case class BundleConfig(location : String, start : Boolean = false, startLevel : Int) {

    private[this] lazy val prettyPrint = s"${getClass.getSimpleName}(location=$location, autoStart=$start, startLevel=$startLevel)"

    override def toString : String = prettyPrint
  }

  private[this] val ConfigPrefix = "blended.launcher.Launcher"

  /**
   * Read and validate the given config object.
   * @return A valid [LauncherConfig] read from the given config.
   */
  def read(config : Config) : LauncherConfig = {

    val optionals = ConfigFactory.parseResources(getClass(), "LauncherConfig-optional.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    val reference = ConfigFactory.parseResources(getClass(), "LauncherConfig-reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    config.withFallback(optionals).checkValid(reference)

    LauncherConfig(
      frameworkJar = config.getString("frameworkBundle"),
      systemProperties = ConfigPropertyMapConverter.getKeyAsPropertyMap(config, "systemProperties", Some(() => Map())),
      frameworkProperties = ConfigPropertyMapConverter.getKeyAsPropertyMap(config, "frameworkProperties", Some(() => Map())),
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
      branding = ConfigPropertyMapConverter.getKeyAsPropertyMap(config, "branding", Some(() => Map()))
    )

  }

  /**
   * Read and validate the given file.
   * @return A valid [LauncherConfig].
   */
  def read(file : File) : LauncherConfig = {
    val config = ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).getConfig(ConfigPrefix).resolve()
    read(config)
  }

  def toConfig(launcherConfig : LauncherConfig) : Config = {
    val config = Map(
      "frameworkBundle" -> launcherConfig.frameworkJar,
      "systemProperties" -> ConfigPropertyMapConverter.propertyMapToConfigValue(launcherConfig.systemProperties),
      "frameworkProperties" -> ConfigPropertyMapConverter.propertyMapToConfigValue(launcherConfig.frameworkProperties),
      "startLevel" -> launcherConfig.startLevel,
      "defaultStartLevel" -> launcherConfig.defaultStartLevel,
      "bundles" -> launcherConfig.bundles.map { b =>
        Map(
          "location" -> b.location,
          "start" -> b.start,
          "startLevel" -> b.startLevel
        ).asJava
      }.asJava,
      "branding" -> ConfigPropertyMapConverter.propertyMapToConfigValue(launcherConfig.branding)
    ).asJava
    ConfigValueFactory.fromMap(config).toConfig
  }

}
