package blended.mgmt.base

import blended.updater.config.RuntimeConfig
import blended.updater.config.OverlayConfig
import blended.updater.config.OverlayRef

sealed trait UpdateAction
final case class StageProfile(runtimeConfig: RuntimeConfig, overlays: Set[OverlayConfig]) extends UpdateAction
final case class ActivateProfile(profileName: String, profileVersion: String, overlays: Set[OverlayRef]) extends UpdateAction
