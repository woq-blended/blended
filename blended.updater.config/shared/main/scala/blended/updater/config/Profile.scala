package blended.updater.config

case class Profile(name: String, version: String, overlays: List[OverlaySet]) {
  require(!overlays.isEmpty, "A Profile must have at least on OverlaySet.")

  def toSingle: List[SingleProfile] = overlays.map(o => SingleProfile(name, version, o))

}

object Profile {

  def fromSingleProfiles(singleProfiles: Seq[SingleProfile]): List[Profile] = {
    val grouped = singleProfiles.groupBy(p => (p.name, p.version))
    grouped.toList.map { case ((n, v), p) => Profile(n, v, p.toList.map(_.overlaySet)) }
  }

}