package blended.security

import javax.security.auth.Subject

trait BlendedPermissionManager {

  def permissions(subject: Subject) : Seq[String]

}
