package blended.updater.config

case class OverlaySet(overlays: List[OverlayRef], state: OverlayState, reason: Option[String] = None)

