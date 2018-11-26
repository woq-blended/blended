package blended.updater.config

/**
 * A group of [[Profile]]s, that share the same name and version.
 * Hence, all profiles in the same group reprent the same runtimeConfig, but different overlays.
 * @param name
 * @param version
 * @param overlays
 */
case class ProfileGroup(name: String, version: String, overlays: List[OverlaySet]) {
  require(!overlays.isEmpty, "A ProfileGroup must have at least one OverlaySet.")

  def toSingle: List[Profile] = overlays.map(o => Profile(name, version, o))

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},overlays=${overlays})"
}

object ProfileGroup {

  def fromSingleProfiles(singleProfiles: Seq[Profile]): List[ProfileGroup] = {
    val grouped = singleProfiles.groupBy(p => (p.name, p.version))
    grouped.toList.map { case ((n, v), p) => ProfileGroup(n, v, p.toList.map(_.overlaySet)) }
  }

}
