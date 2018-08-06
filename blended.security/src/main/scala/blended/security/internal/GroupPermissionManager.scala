package blended.security.internal

import blended.security.{BlendedPermission, BlendedPermissionManager, BlendedPermissions}
import blended.security.boot.GroupPrincipal
import javax.security.auth.Subject

import scala.collection.JavaConverters._

class GroupPermissionManager extends BlendedPermissionManager {

  override def permissions(subject: Subject): BlendedPermissions = BlendedPermissions(
    subject.getPrincipals(classOf[GroupPrincipal]).asScala.map { g =>
      BlendedPermission(permissionClass = g.getName())
    }.toSeq
  )
}
