package blended.updater.config

object UpdateAction {
  val KindAddOverlayConfig = classOf[AddOverlayConfig].getSimpleName()
  val KindAddRuntimeConfig = classOf[AddRuntimeConfig].getSimpleName()
  val KindStageProfile = classOf[StageProfile].getSimpleName()
  val KindActivateProfile = classOf[ActivateProfile].getSimpleName()
}

sealed trait UpdateAction {
  def id : String

  /**
   * Copy this action and use the given ID.
   */
  def withId(id : String) : UpdateAction = this match {
    case a : AddRuntimeConfig =>
      a.copy(id = id)
    case a : AddOverlayConfig =>
      a.copy(id = id)
    case a : StageProfile =>
      a.copy(id = id)
    case a : ActivateProfile =>
      a.copy(id = id)
  }
}

final case class AddRuntimeConfig(
  id : String,
  runtimeConfig : RuntimeConfig
) extends UpdateAction

final case class AddOverlayConfig(
  id : String,
  overlay : OverlayConfig
) extends UpdateAction

final case class StageProfile(
  id : String,
  profileName : String,
  profileVersion : String,
  overlays : Set[OverlayRef]
) extends UpdateAction

final case class ActivateProfile(
  id : String,
  profileName : String,
  profileVersion : String,
  overlays : Set[OverlayRef]
) extends UpdateAction

/**
 * Message published to the event stream when an update action with the same `id` was applied.
 */
case class UpdateActionApplied(id : String, error : Option[String] = None)
