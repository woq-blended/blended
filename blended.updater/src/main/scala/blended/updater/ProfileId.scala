package blended.updater

import blended.updater.config.OverlayRef

/**
 * A ProfileId is a concrete runtime config with one set of overlays.
 */
case class ProfileId(name: String, version: String, overlays: List[OverlayRef]) {
  override def toString(): String =
    s"${name}-${version}_" + {
      if (overlays.isEmpty) "base"
      else overlays.toList.sorted.mkString("_")
    }
}