package blended.updater

import blended.updater.config.OverlayRef

trait ProfileActivator {
  def apply(name: String, version: String, overlays: List[OverlayRef]): Boolean
}