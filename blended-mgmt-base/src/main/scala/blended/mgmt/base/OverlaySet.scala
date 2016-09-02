package blended.mgmt.base

import blended.updater.config.OverlayRef

case class OverlaySet(overlays: Seq[OverlayRef], state: OverlayState, reason: Option[String] = None)
