package blended.updater.config

import upickle.Js
import upickle.default._

object UpdateAction {
  val KindAddOverlayConfig = classOf[AddOverlayConfig].getSimpleName()
  val KindAddRuntimeConfig = classOf[AddRuntimeConfig].getSimpleName()
  val KindStageProfile = classOf[StageProfile].getSimpleName()
  val KindActivateProfile = classOf[ActivateProfile].getSimpleName()

  implicit val updateActionWriter = Writer[UpdateAction] {
    case ua => ua match {
      case a: StageProfile => writeJs(a)
      case a: ActivateProfile => writeJs(a)
      case a: AddRuntimeConfig => writeJs(a)
      case a: AddOverlayConfig => writeJs(a)
      case _ => throw new SerializationException(s"Could not write object ${ua}")
    }
  }

  implicit val updateActionReader = Reader[UpdateAction] {
    case Js.Obj(obj) =>
      println(obj)
      ActivateProfile("foo", "bar", List.empty)
    case _=> throw new SerializationException(s"Could not deserialize UpdateAction")
  }
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
  overlays: List[OverlayRef]
) extends UpdateAction

final case class ActivateProfile(
  profileName: String,
  profileVersion: String,
  overlays: List[OverlayRef]
) extends UpdateAction
