package blended.updater.config

object UpdateAction {
  val KindAddRuntimeConfig: String = classOf[AddRuntimeConfig].getSimpleName()
  val KindStageProfile: String = classOf[StageProfile].getSimpleName()
  val KindActivateProfile: String = classOf[ActivateProfile].getSimpleName()
}

sealed trait UpdateAction {
  def id: String

  /**
   * Copy this action and use the given ID.
   */
  def withId(id: String): UpdateAction = this match {
    case a: AddRuntimeConfig =>
      a.copy(id = id)
    case a: StageProfile =>
      a.copy(id = id)
    case a: ActivateProfile =>
      a.copy(id = id)
  }
}

final case class AddRuntimeConfig(
    id: String,
    runtimeConfig: Profile
) extends UpdateAction

final case class StageProfile(
    id: String,
    profileName: String,
    profileVersion: String
) extends UpdateAction

final case class ActivateProfile(
    id: String,
    profileName: String,
    profileVersion: String
) extends UpdateAction

/**
 * Message published to the event stream when an update action with the same `id` was applied.
 */
case class UpdateActionApplied(id: String, error: Option[String] = None)
