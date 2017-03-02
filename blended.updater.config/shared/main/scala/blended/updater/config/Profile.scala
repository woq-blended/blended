package blended.updater.config

case class Profile(name: String, version: String, overlays: List[OverlaySet]) {

  def toSingle: List[Profile.SingleProfile] = overlays.map(o => Profile.SingleProfile(name, version, o))

}

object Profile {

  case class SingleProfile(name: String, version: String, overlaySet: OverlaySet) {
    def overlays: List[OverlayRef] = overlaySet.overlays
    def state: OverlayState = overlaySet.state
  }

}