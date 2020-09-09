package blended.updater

import blended.updater.config._

object StatefulLocalProfile {

  sealed trait ProfileState

  /**
   * Profile configs are present, but artifacts are not yet resolved
   */
  final case class Pending(issues: List[String]) extends ProfileState

  /**
   * Profile configs are present, but there were issues.
   */
  final case class Invalid(issues: List[String]) extends ProfileState

  //
  //  /**
  //   *
  //   */
  //  final case object Resolved extends ProfileState

  /**
   * Profile configs and all required artifacts are resolved.
   */
  final case object Staged extends ProfileState

}

case class StatefulLocalProfile(
    config: LocalProfile,
    state: StatefulLocalProfile.ProfileState
) {
  def profileId: ProfileRef = ProfileRef(config.runtimeConfig.name, config.runtimeConfig.version)

  def runtimeConfig: Profile = config.resolvedProfile.profile

  def bundles: List[BundleConfig] = config.resolvedProfile.allBundles.get

  def toSingleProfile: ProfileRef = {
    val (oState, reason) = state match {
      case StatefulLocalProfile.Pending(issues) => (OverlayState.Pending, Some(issues.mkString("; ")))
      case StatefulLocalProfile.Invalid(issues) => (OverlayState.Invalid, Some(issues.mkString("; ")))
      //      case LocalProfile.Resolved => (OverlayState.Valid, None)
      case StatefulLocalProfile.Staged => (OverlayState.Valid, None)
    }

    ProfileRef(
      config.runtimeConfig.name,
      config.runtimeConfig.version
    )
  }

}
