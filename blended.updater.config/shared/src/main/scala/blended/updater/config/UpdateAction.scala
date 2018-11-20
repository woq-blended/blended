package blended.updater.config

object UpdateAction {
  val KindAddOverlayConfig = classOf[AddOverlayConfig].getSimpleName()
  val KindAddRuntimeConfig = classOf[AddRuntimeConfig].getSimpleName()
  val KindStageProfile = classOf[StageProfile].getSimpleName()
  val KindActivateProfile = classOf[ActivateProfile].getSimpleName()
}

sealed trait UpdateAction

final case class AddRuntimeConfig(
  runtimeConfig: RuntimeConfig
) extends UpdateAction

final case class AddOverlayConfig(
  overlay: OverlayConfig
) extends UpdateAction

final case class StageProfile(
  profileName: String,
  profileVersion: String,
  overlays: Set[OverlayRef]
) extends UpdateAction

final case class ActivateProfile(
  profileName: String,
  profileVersion: String,
  overlays: Set[OverlayRef]
) extends UpdateAction
