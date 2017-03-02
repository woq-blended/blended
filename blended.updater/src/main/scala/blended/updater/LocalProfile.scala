package blended.updater

import blended.updater.config.BundleConfig
import blended.updater.config.LocalOverlays
import blended.updater.config.LocalRuntimeConfig
import blended.updater.config.RuntimeConfig

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
}

