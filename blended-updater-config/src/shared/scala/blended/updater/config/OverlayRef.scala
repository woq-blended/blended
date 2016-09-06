package blended.updater.config

/**
 * A reference to an overlay config.
 *
 * @param name    The name of the overlay.
 * @param version The version of the overlay.
 */
final case class OverlayRef(name: String, version: String) extends Ordered[OverlayRef] {
  override def toString(): String = name + ":" + version

  override def compare(other: OverlayRef): Int = toString().compare(other.toString())
}