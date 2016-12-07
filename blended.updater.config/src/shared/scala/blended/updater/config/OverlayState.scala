package blended.updater.config

import upickle.Js

sealed trait OverlayState {
  val state: String
}

object OverlayState {

  final case object Active extends OverlayState {
    override val state: String = "active"
  }
  final case object Valid extends OverlayState {
    override val state: String = "valid"
  }
  final case object Invalid extends OverlayState {
    override val state: String = "invalid"
  }
  final case object Pending extends OverlayState {
    override val state: String = "pending"
  }

  implicit val os2writer = upickle.default.Writer[OverlayState]{
    case state => Js.Str(state.state)
  }

  implicit val os2Reader = upickle.default.Reader[OverlayState]{
    case Js.Str(Active.state) => Active
    case Js.Str(Valid.state) => Valid
    case Js.Str(Invalid.state) => Invalid
    case Js.Str(Pending.state) => Pending
  }

}

