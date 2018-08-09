package blended.security.internal

import blended.security.{BlendedPermissionManager, BlendedPermissions}
import javax.security.auth.Subject

class ConfigPermissionManager extends BlendedPermissionManager {
  override def permissions(subject: Subject): BlendedPermissions = BlendedPermissions(Seq.empty)
}
