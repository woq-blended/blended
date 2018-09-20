package blended.updater.config

case class SingleProfile(name: String, version: String, overlaySet: OverlaySet) {
  // convenience getters
  def overlays: List[OverlayRef] = overlaySet.overlays
  def state: OverlayState = overlaySet.state
}