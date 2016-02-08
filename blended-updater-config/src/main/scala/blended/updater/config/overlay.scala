package blended.updater.config

import com.typesafe.config.Config
import _root_.scala.collection.immutable
import java.io.File
import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.Left
import scala.util.Right
import com.typesafe.config.ConfigFactory

final case class OverlayRef(name: String, version: String) {
  override def toString(): String = name + ":" + version
}

final case class OverlayConfig(
    name: String,
    version: String,
    generatedConfigs: immutable.Seq[GeneratedConfig] = immutable.Seq()) {

  def overlayRef: OverlayRef = OverlayRef(name, version)

  // TODO: check collision free generators
  {

  }

  def validate(): Seq[String] = {
    OverlayConfig.findCollisions(generatedConfigs)
  }

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},generatedConfigs=${generatedConfigs})"

}

final object OverlayConfig {

  def findCollisions(generatedConfigs: Seq[GeneratedConfig]): Seq[String] = {
    aggregateGeneratedConfigs(generatedConfigs) match {
      case Left(issues) => issues
      case _ => Nil
    }
  }

  def aggregateGeneratedConfigs(generatedConfigs: Seq[GeneratedConfig]): Either[Seq[String], Map[String, Map[String, Object]]] = {
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
          // TODO: check collisions
          val collisions = existingConfig.keySet.intersect(newConfig.keySet)
          fileToConfig += gc.configFile -> (existingConfig ++ newConfig)
          collisions.map(c => s"Double defined config key found: ${c}")
      }
    }
    if (issues.isEmpty) Right(fileToConfig) else Left(issues)
  }

}

/**
 * A materialized set of overlays.
 */
final case class LocalOverlays(overlays: immutable.Seq[OverlayConfig], profileDir: File) {

  // TODO: check collisions
  def validate(): Seq[String] = {
    val nameIssues = overlays.groupBy(_.name).collect {
      case (name, configs) if configs.size > 1 => s"More than one overlay with name '${name}' detected"
    }.toList
    val generatorIssues = OverlayConfig.findCollisions(overlays.flatMap(_.generatedConfigs))
    nameIssues ++ generatorIssues
  }

  /**
   * The location of the final applied set of overlays.
   */
  def materializedDir: File = LocalOverlays.materializedDir(overlays.map(_.overlayRef), profileDir)

  def materialize(): Try[Unit] = Try {
    val dir = materializedDir
    OverlayConfig.aggregateGeneratedConfigs(overlays.flatMap(_.generatedConfigs)) match {
      case Left(issues) =>
        sys.error("Cannot materialize invalid or inconsistent overlays. Issues: " + issues.mkString(";"))
      case Right(configByFile) =>
        configByFile.foreach {
          case (fileName, config) =>
            val file = new File(dir, fileName)
            file.getParentFile().mkdirs()
            val configFileContent = ConfigFactory.parseMap(config.asJava)
            ConfigWriter.write(configFileContent, file, None)
        }
    }
  }

}

final object LocalOverlays {
  def materializedDir(overlays: Seq[OverlayRef], profileDir: File): File = {
    if (overlays.isEmpty) {
      profileDir
    } else {
      val overlayParts = overlays.map(o => s"${o.name}-${o.version}").distinct.sorted
      new File(profileDir, overlayParts.mkString("/"))
    }
  }
}

case class GeneratedConfig(configFile: String, config: Config) {

}