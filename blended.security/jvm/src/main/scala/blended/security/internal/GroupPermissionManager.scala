package blended.security.internal

import blended.security.boot.GroupPrincipal
import blended.security.{BlendedPermission, BlendedPermissionManager, BlendedPermissions}
import javax.security.auth.Subject

import scala.jdk.CollectionConverters._

class GroupPermissionManager extends BlendedPermissionManager {

  override def permissions(subject : Subject) : BlendedPermissions = BlendedPermissions(
    subject.getPrincipals(classOf[GroupPrincipal]).asScala.map { g =>
      BlendedPermission(permissionClass = Some(g.getName()))
    }.toList
  )
}
