package blended.updater.config

case class Profile(name : String, version : String, overlaySet : OverlaySet) {
  // convenience getters
  def overlays : Set[OverlayRef] = overlaySet.overlays
  def state : OverlayState = overlaySet.state

  override def toString() : String = s"${getClass().getSimpleName()}(name=${name},version=${version},overlaySet=${overlaySet})}"
}
