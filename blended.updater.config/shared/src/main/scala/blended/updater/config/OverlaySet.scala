package blended.updater.config

/**
 * A set of overlay reference that are meant to be used together.
 * The empty list represents together with an [[RuntimeConfig]] represents an deployable base profile.
 */
case class OverlaySet(overlays: List[OverlayRef], state: OverlayState, reason: Option[String] = None) {
  override def toString(): String = s"${getClass().getSimpleName()}(overlays=${overlays},state=${state},reason=${reason})"
}
