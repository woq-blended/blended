package blended.updater

/**
 * A ProfileId is a concrete runtime config with one set of overlays.
 */
case class ProfileId(name : String, version : String) {
  override def toString() : String =
    s"${name}-${version}"
}
