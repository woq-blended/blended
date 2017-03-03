package blended.updater.config

case class Profile(name: String, version: String, overlays: List[OverlaySet]) {

  def toSingle: List[Profile.SingleProfile] = overlays.map(o => Profile.SingleProfile(name, version, o))

}

object Profile {

  case class SingleProfile(name: String, version: String, overlaySet: OverlaySet) {
    def overlays: List[OverlayRef] = overlaySet.overlays
    def state: OverlayState = overlaySet.state
  }

  def fromSingleProfiles(singleProfiles: Seq[SingleProfile]): List[Profile] = {
    val grouped = singleProfiles.groupBy(p => (p.name, p.version))
    grouped.toList.map { case ((n, v), p) => Profile(n, v, p.toList.map(_.overlaySet)) }
  }

}