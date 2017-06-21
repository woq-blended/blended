package blended.mgmt.ui.components.filter

import blended.mgmt.ui.util.Logger
import blended.updater.config.OverlayConfig

object OverlayConfigFilter {

  private[this] val log = Logger[OverlayConfigFilter.type]

  case class Name(name: String, exact: Boolean = false) extends Filter[OverlayConfig] {
    override def matches(profile: OverlayConfig): Boolean =
      if (exact) profile.name == name
      else profile.name.contains(name)
  }

  case class Version(version: String, exact: Boolean = false) extends Filter[OverlayConfig] {
    override def matches(profile: OverlayConfig): Boolean =
      if (exact) profile.version == version
      else profile.version.contains(version)
  }

}