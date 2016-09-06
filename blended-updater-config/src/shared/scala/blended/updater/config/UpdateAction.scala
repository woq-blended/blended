package blended.updater.config

object UpdateAction {
  val KindAddOverlayConfig = classOf[AddOverlayConfig].getSimpleName()
  val KindAddRuntimeConfig = classOf[AddRuntimeConfig].getSimpleName()
  val KindStageProfile = classOf[StageProfile].getSimpleName()
  val KindActivateProfile = classOf[ActivateProfile].getSimpleName()
}

sealed trait UpdateAction {
  def kind: String
}

final case class AddRuntimeConfig(
  runtimeConfig: RuntimeConfig,
  kind: String = UpdateAction.KindAddRuntimeConfig)
    extends UpdateAction {
  require(kind == getClass.getSimpleName(), s"kind must be ${getClass.getSimpleName()} but was: ${kind}")
}

final case class AddOverlayConfig(
  overlay: OverlayConfig,
  kind: String = UpdateAction.KindAddOverlayConfig) extends UpdateAction {
  require(kind == getClass.getSimpleName(), s"kind must be ${getClass.getSimpleName()} but was: ${kind}")
}

final case class StageProfile(
  profileName: String,
  profileVersion: String,
  overlays: Set[OverlayRef],
  kind: String = UpdateAction.KindStageProfile)
    extends UpdateAction {
  require(kind == getClass.getSimpleName(), s"kind must be ${getClass.getSimpleName()} but was: ${kind}")
}

final case class ActivateProfile(
  profileName: String,
  profileVersion: String,
  overlays: Set[OverlayRef],
  kind: String = UpdateAction.KindActivateProfile)
    extends UpdateAction {
  require(kind == getClass.getSimpleName(), s"kind must be ${getClass.getSimpleName()} but was: ${kind}")
}