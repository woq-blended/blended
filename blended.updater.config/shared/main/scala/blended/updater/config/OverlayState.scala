package blended.updater.config

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

  def fromString(state: String): Option[OverlayState] = 
    List(Active, Valid, Invalid, Pending).find(s => s.state == state)

}

