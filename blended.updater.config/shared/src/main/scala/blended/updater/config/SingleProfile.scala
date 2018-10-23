package blended.updater.config

case class SingleProfile(name: String, version: String, overlaySet: OverlaySet) {
  // convenience getters
  def overlays: List[OverlayRef] = overlaySet.overlays
  def state: OverlayState = overlaySet.state

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},overlaySet=${overlaySet})}"
}
