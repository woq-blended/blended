package blended.updater.config

import blended.security.{BlendedPermission, GrantableObject}

import scala.collection.{immutable => sci}

case class ContainerInfo(
  containerId: String,
  properties: Map[String, String],
  serviceInfos: List[ServiceInfo],
  profiles: List[Profile],
  timestampMsec: Long,
  appliedUpdateActionIds: List[String]
) extends GrantableObject {

  override def permission: BlendedPermission = BlendedPermission(
    permissionClass = Some("container"),
    properties = properties.map { case (k, v) => k -> sci.Seq(v) }
  )

  override def toString(): String = s"${getClass().getSimpleName()}(containerId=${containerId},properties=${properties},serviceInfos=${serviceInfos},profiles=${profiles},timestampMsec=${timestampMsec},appliedUpdateActionIds=${appliedUpdateActionIds})"

}
