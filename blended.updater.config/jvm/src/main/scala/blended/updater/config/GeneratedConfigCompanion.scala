package blended.updater.config

import scala.collection.immutable.Map
import scala.jdk.CollectionConverters._
import scala.util.{Left, Right}

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

object GeneratedConfigCompanion {

  def create(filename : String, cfg : Config) : GeneratedConfig = {

    val json = cfg.root().render(ConfigRenderOptions.concise())
    GeneratedConfig(filename, json)
  }

  def config(cfg : GeneratedConfig) : Config = ConfigFactory.parseString(cfg.config)


  def findCollisions(generatedConfigs : Seq[GeneratedConfig]) : Seq[String] = {
    aggregateGeneratedConfigs(generatedConfigs) match {
      case Left(issues) => issues
      case _            => Nil
    }
  }

  private def aggregateGeneratedConfigs(generatedConfigs : Iterable[GeneratedConfig]) : Either[Seq[String], Map[String, Map[String, Object]]] = {
    // seen configurations per target file
    var fileToConfig : Map[String, Map[String, Object]] = Map()
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

  def aggregateGeneratedConfigs2(generatedConfigs : Iterable[GeneratedConfig]) : Either[Seq[String], Map[String, Config]] = {
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

}
