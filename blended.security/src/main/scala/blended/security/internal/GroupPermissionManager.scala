package blended.security.internal

import blended.security.BlendedPermissionManager
import javax.security.auth.Subject

//TODO: How do we map Groups to Permission strings ?
class GroupPermissionManager extends BlendedPermissionManager {

  override def permissions(subject: Subject): List[String] = List.empty
}
