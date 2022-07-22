package blended.updater.config

import blended.security.{BlendedPermission, GrantableObject}

case class ContainerInfo(
                          containerId: String,
                          properties: Map[String, String],
                          serviceInfos: List[ServiceInfo],
                          profiles: List[ProfileRef],
                          timestampMsec: Long
                        ) extends GrantableObject {

  override def permission: BlendedPermission = BlendedPermission(
    permissionClass = Some("container"),
    properties = properties.map { case (k, v) => k -> scala.collection.immutable.Seq(v) }
  )

  override def toString(): String =
    getClass().getSimpleName() +
      "(containerId=" + containerId +
      ",properties=" + properties +
      ",serviceInfos=" + serviceInfos +
      ",profiles=" + profiles +
      ",timestampMsec=" + timestampMsec +
      ")"

}
