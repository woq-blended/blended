package blended.updater.config

import upickle.default._

case class OverlaySet(overlays: List[OverlayRef], state: OverlayState, reason: Option[String] = None)

object OverlaySet {
  implicit val readWriter: ReadWriter[OverlaySet] = macroRW
}
