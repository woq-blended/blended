package blended.updater.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.collection.immutable.Map
import scala.util.Left
import scala.util.Right
import scala.util.Try

/**
 * A reference to an overlay config.
 *
 * @param name    The name of the overlay.
 * @param version The version of the overlay.
 */
final case class OverlayRef(name: String, version: String) extends Ordered[OverlayRef] {
  override def toString(): String = name + "-" + version

  override def compare(other: OverlayRef): Int = toString().compare(other.toString())
}

/**
 * Definition of an overlay.
 *
 * @param name             The name of the overlay.
 * @param version          The version of the overlay.
 * @param generatedConfigs The config file generators.
 */
final case class OverlayConfig(
  name: String,
  version: String,
  generatedConfigs: immutable.Seq[GeneratedConfig] = immutable.Seq(),
  properties: Map[String, String] = Map()
) extends Ordered[OverlayConfig] {

  override def compare(other: OverlayConfig): Int = overlayRef.compare(other.overlayRef)

  def overlayRef: OverlayRef = OverlayRef(name, version)

  def validate(): Seq[String] = {
    OverlayConfig.findCollisions(generatedConfigs)
  }

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},generatedConfigs=${generatedConfigs})"

}

/**
 * Companion for [[OverlayConfig]] containing common useful operations.
 */
final object OverlayConfig extends ((String, String, immutable.Seq[GeneratedConfig], Map[String, String]) => OverlayConfig) {

  object Properties {
    val JVM_MAX_MEM = "blended.launcher.jvm.xmx"
    val JVM_USE_MEM = "blended.launcher.jvm.xms"
  }

  def findCollisions(generatedConfigs: Seq[GeneratedConfig]): Seq[String] = {
    aggregateGeneratedConfigs(generatedConfigs) match {
      case Left(issues) => issues
      case _ => Nil
    }
  }

  def aggregateGeneratedConfigs(generatedConfigs: Iterable[GeneratedConfig]): Either[Seq[String], Map[String, Map[String, Object]]] = {
    // seen configurations per target file
    var fileToConfig: Map[String, Map[String, Object]] = Map()
    val issues = generatedConfigs.flatMap { gc =>
      val newConfig = gc.config.root().unwrapped().asScala.toMap
      fileToConfig.get(gc.configFile) match {
        case None =>
          // no collision
          fileToConfig += gc.configFile -> newConfig
          Seq()
        case Some(existingConfig) =>
          // check collisions
          val collisions = existingConfig.keySet.intersect(newConfig.keySet)
          fileToConfig += gc.configFile -> (existingConfig ++ newConfig)
          collisions.map(c => s"Double defined config key found: ${c}")
      }
    }
    if (issues.isEmpty) Right(fileToConfig) else Left(issues.toList)
  }


  def read(config: Config): Try[OverlayConfig] = Try {

    def configAsMap(key: String, default: Option[() => Map[String, String]] = None): Map[String, String] =
      if (default.isDefined && !config.hasPath(key)) {
        default.get.apply()
      } else {
        config.getConfig(key).entrySet().asScala.map {
          entry => entry.getKey() -> entry.getValue().unwrapped().asInstanceOf[String]
        }.toMap
      }

    OverlayConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      generatedConfigs = if (config.hasPath("configGenerator")) {
        config.getObjectList("configGenerator").asScala.map { gen =>
          val genConf = gen.toConfig()
          val fileName = genConf.getString("file")
          val genConfig = genConf.getObject("config").toConfig()
          GeneratedConfig(configFile = fileName, config = genConfig)
        }.toList
      } else Nil,
      properties = configAsMap("properties", Some(() => Map()))
    )
  }

  def toConfig(overlayConfig: OverlayConfig): Config = {
    val config = Map(
      "name" -> overlayConfig.name,
      "version" -> overlayConfig.version,
      "configGenerator" -> overlayConfig.generatedConfigs.map { genConfig =>
        Map(
          "file" -> genConfig.configFile,
          "config" -> genConfig.config.root().unwrapped()
        ).asJava
      }.asJava,
      "properties" -> overlayConfig.properties.asJava
    ).asJava
    ConfigFactory.parseMap(config)
  }

}
