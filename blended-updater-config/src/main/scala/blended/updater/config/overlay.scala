package blended.updater.config

import com.typesafe.config.Config
import _root_.scala.collection.immutable
import java.io.File
import scala.collection.JavaConverters._

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

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},generatedConfigs=${generatedConfigs})"

}

final object OverlayConfig {

  def findCollisions(generatedConfigs: Seq[GeneratedConfig]): Seq[String] = {
    // seen configurations per target file
    var fileToConfig: Map[String, Map[String, Object]] = Map()
    generatedConfigs.flatMap { gc =>
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
  }

}

/**
 * A materialized set of overlays.
 */
final case class LocalOverlays(overlays: immutable.Seq[OverlayConfig], profileDir: File) {

  // TODO: check collisions
  def validate(): Seq[String] = {
    overlays.groupBy(_.name).collect {
      case (name, configs) if configs.size > 1 => s"More than one overlay with name '${name}' detected"
    }.toList
  }

  /**
   * The location of the final applied set of overlays.
   */
  def materializedDir: File = LocalOverlays.materializedDir(overlays.map(_.overlayRef), profileDir)

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