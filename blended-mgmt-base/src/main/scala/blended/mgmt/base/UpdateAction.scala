package blended.mgmt.base

import blended.updater.config.RuntimeConfig

sealed trait UpdateAction
final case class StageProfile(runtimeConfig: RuntimeConfig) extends UpdateAction
final case class ActivateProfile(profileName: String, profileVersion: String) extends UpdateAction
