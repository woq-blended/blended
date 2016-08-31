package blended.mgmt.base

import scala.collection.immutable

case class NameVersion(name: String, version: String)

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

case class OverlaySet(overlays: Seq[NameVersion], state: OverlayState, reason: Option[String])

case class Profile(name: String, version: String, overlays: Seq[OverlaySet])

case class ContainerInfo(
    containerId: String,
    properties: Map[String, String],
    serviceInfos: immutable.Seq[ServiceInfo],
    profiles: Seq[Profile]) {

  override def toString(): String = s"${getClass().getSimpleName()}(containerId=${containerId},properties=${properties},serviceInfos=${serviceInfos},profiles=${profiles})"

}


