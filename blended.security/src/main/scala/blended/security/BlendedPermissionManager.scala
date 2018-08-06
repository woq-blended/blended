package blended.security

import javax.security.auth.Subject

case class BlendedPermission(

)

trait BlendedPermissionManager {

  def permissions(subject: Subject) : Seq[String]

}
