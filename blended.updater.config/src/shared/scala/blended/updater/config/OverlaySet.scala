package blended.updater.config

import scala.collection.immutable

case class OverlaySet(overlays: immutable.Seq[OverlayRef], state: OverlayState, reason: Option[String] = None)
