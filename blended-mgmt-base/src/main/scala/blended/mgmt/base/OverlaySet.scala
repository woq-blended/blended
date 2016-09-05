package blended.mgmt.base

import scala.collection.immutable
import blended.updater.config.OverlayRef

case class OverlaySet(overlays: immutable.Seq[OverlayRef], state: OverlayState, reason: Option[String] = None)
