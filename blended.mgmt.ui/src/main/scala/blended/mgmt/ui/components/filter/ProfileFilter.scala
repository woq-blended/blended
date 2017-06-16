package blended.mgmt.ui.components.filter

import blended.updater.config.ContainerInfo
import java.util.regex.Pattern
import scala.collection.immutable.Seq
import blended.mgmt.ui.util.Logger
import blended.updater.config.Profile

object ProfileFilter {

  private[this] val log = Logger[ProfileFilter.type]

  case class ProfileName(name: String, exact: Boolean = false) extends Filter[Profile] {
    override def matches(profile: Profile): Boolean =
      if (exact) profile.name == name
      else profile.name.contains(name)
  }
  
  case class ProfileVersion(version: String, exact: Boolean = false) extends Filter[Profile] {
    override def matches(profile: Profile): Boolean =
      if (exact) profile.version == version
      else profile.version.contains(version)
  }

}