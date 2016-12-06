package blended.updater.config

sealed trait OverlayState {
  def state: String
}

object OverlayState {
  final case object Active extends OverlayState {
    override val state: String = "active"
  }
  final case object Valid extends OverlayState {
    override val state: String = "valid"
  }
  final case object Invalid extends OverlayState {
    override def state: String = "invalid"
  }
  final case object Pending extends OverlayState {
    override def state: String = "pending"
  }
}

