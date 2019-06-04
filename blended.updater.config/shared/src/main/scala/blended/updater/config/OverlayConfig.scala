package blended.updater.config

/**
 * Definition of an overlay.
 *
 * @param name
 * 	 The name of the overlay.
 * @param version
 * 	 The version of the overlay.
 * @param generatedConfigs
 *   The config file generators.
 * @param properties
 *   Additional system properties.
 */
final case class OverlayConfig(
  name : String,
  version : String,
  generatedConfigs : List[GeneratedConfig] = List.empty,
  properties : Map[String, String] = Map.empty
) extends Ordered[OverlayConfig] {

  override def compare(other : OverlayConfig) : Int = overlayRef.compare(other.overlayRef)

  def overlayRef : OverlayRef = OverlayRef(name, version)

  override def toString() : String = getClass().getSimpleName() +
    "(name=" + name +
    ",version=" + version +
    ",generatedConfigs=" + generatedConfigs +
    ",properties=" + properties +
    ")"
}
