package blended.mgmt.base

import blended.updater.config.RuntimeConfig
import blended.updater.config.OverlayConfig
import blended.updater.config.OverlayRef

sealed trait UpdateAction {
  def kind: String
}

final case class AddRuntimeConfig(
  runtimeConfig: RuntimeConfig,
  kind: String = classOf[AddRuntimeConfig].getSimpleName())
  extends UpdateAction {
  require(kind == getClass.getSimpleName(), s"kind must be ${getClass.getSimpleName()} but was: ${kind}")
}

final case class AddOverlayConfig(
  overlay: OverlayConfig,
  kind: String = classOf[AddOverlayConfig].getSimpleName())
  extends UpdateAction {
  require(kind == getClass.getSimpleName(), s"kind must be ${getClass.getSimpleName()} but was: ${kind}")
}

final case class StageProfile(
  profileName: String,
  profileVersion: String,
  overlays: Set[OverlayRef],
  kind: String = classOf[StageProfile].getSimpleName())
  extends UpdateAction {
  require(kind == getClass.getSimpleName(), s"kind must be ${getClass.getSimpleName()} but was: ${kind}")
}

final case class ActivateProfile(
  profileName: String,
  profileVersion: String,
  overlays: Set[OverlayRef],
  kind: String = classOf[ActivateProfile].getSimpleName())
  extends UpdateAction {
  require(kind == getClass.getSimpleName(), s"kind must be ${getClass.getSimpleName()} but was: ${kind}")
}