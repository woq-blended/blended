package blended.updater.config

import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConverters._
import scala.collection.immutable.Map
import scala.util.{ Left, Right, Try }
import blended.updater.config.util.ConfigPropertyMapConverter

/**
 * Helper for [[OverlayConfig]] containing common useful operations.
 */
final object OverlayConfigCompanion {

  object Properties {
    val JVM_MAX_MEM = "blended.launcher.jvm.xmx"
    val JVM_USE_MEM = "blended.launcher.jvm.xms"
    val JVM_STACK_SIZE = "blended.launcher.jvm.ss"
  }

  def findCollisions(generatedConfigs: Seq[GeneratedConfig]): Seq[String] = {
    aggregateGeneratedConfigs(generatedConfigs) match {
      case Left(issues) => issues
      case _ => Nil
    }
  }

  private def aggregateGeneratedConfigs(generatedConfigs: Iterable[GeneratedConfig]): Either[Seq[String], Map[String, Map[String, Object]]] = {
    // seen configurations per target file
    var fileToConfig: Map[String, Map[String, Object]] = Map()
    val issues = generatedConfigs.flatMap { gc =>
      val newConfig = GeneratedConfigCompanion.config(gc).root().unwrapped().asScala.toMap
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

  def aggregateGeneratedConfigs2(generatedConfigs: Iterable[GeneratedConfig]): Either[Seq[String], Map[String, Config]] = {
    aggregateGeneratedConfigs(generatedConfigs) match {
      case Left(issues) =>
        // issue pass through
        Left(issues)
      case Right(fileToMap) =>
        // instead of a Map we aggregate a Config via Config API (to avoid structural changes)
        Right(
          generatedConfigs.foldLeft(Map[String, Config]()) { (l, gc) =>
            val config = GeneratedConfigCompanion.config(gc)
            l.get(gc.configFile) match {
              case None =>
                l ++ Map(gc.configFile -> config)
              case Some(existingConfig) =>
                val mergedConfig = config.withFallback(existingConfig)
                l ++ Map(gc.configFile -> mergedConfig)
            }
          }
        )
    }
  }

  def read(config: Config): Try[OverlayConfig] = Try {

    OverlayConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      generatedConfigs = if (config.hasPath("configGenerator")) {
        config.getObjectList("configGenerator").asScala.map { gen =>
          val genConf = gen.toConfig()
          val fileName = genConf.getString("file")
          val genConfig = genConf.getObject("config").toConfig()
          GeneratedConfigCompanion.create(fileName, genConfig)
        }.toList
      } else Nil,
      properties = ConfigPropertyMapConverter.getKeyAsPropertyMap(config, "properties", Some(() => Map()))
    )
  }

  def toConfig(overlayConfig: OverlayConfig): Config = {
    val config = Map(
      "name" -> overlayConfig.name,
      "version" -> overlayConfig.version,
      "configGenerator" -> overlayConfig.generatedConfigs.map { genConfig =>
        Map(
          "file" -> genConfig.configFile,
          "config" -> GeneratedConfigCompanion.config(genConfig).root().unwrapped()
        ).asJava
      }.asJava,
      "properties" -> ConfigPropertyMapConverter.propertyMapToConfigValue(overlayConfig.properties)
    ).asJava
    ConfigFactory.parseMap(config)
  }

}
