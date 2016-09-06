package blended.updater.config

import scala.collection.immutable
import scala.collection.immutable.Map

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

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},generatedConfigs=${generatedConfigs})"
}
