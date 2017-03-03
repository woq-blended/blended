package blended.updater

import blended.updater.config.BundleConfig
import blended.updater.config.LocalOverlays
import blended.updater.config.LocalRuntimeConfig
import blended.updater.config.RuntimeConfig
import blended.updater.config.Profile.SingleProfile
import blended.updater.config.OverlayState
import blended.updater.config.OverlaySet

object LocalProfile {

  sealed trait ProfileState

  final case class Pending(issues: List[String]) extends ProfileState

  final case class Invalid(issues: List[String]) extends ProfileState

  final case object Resolved extends ProfileState

  final case object Staged extends ProfileState

}

case class LocalProfile(config: LocalRuntimeConfig, overlays: LocalOverlays, state: LocalProfile.ProfileState) {
  def profileId: ProfileId = ProfileId(config.runtimeConfig.name, config.runtimeConfig.version, overlays.overlayRefs)

  def runtimeConfig: RuntimeConfig = config.resolvedRuntimeConfig.runtimeConfig

  def bundles: List[BundleConfig] = config.resolvedRuntimeConfig.allBundles

  def toSingleProfile: SingleProfile = {
    val (oState, reason) = state match {
      case LocalProfile.Pending(issues) => (OverlayState.Pending, Some(issues.mkString("; ")))
      case LocalProfile.Invalid(issues) => (OverlayState.Invalid, Some(issues.mkString("; ")))
      case LocalProfile.Resolved => (OverlayState.Valid, None)
      case LocalProfile.Staged => (OverlayState.Pending, None)
    }

    SingleProfile(
      config.runtimeConfig.name,
      config.runtimeConfig.version,
      OverlaySet(overlays.overlayRefs, oState, reason)
    )
  }

}

