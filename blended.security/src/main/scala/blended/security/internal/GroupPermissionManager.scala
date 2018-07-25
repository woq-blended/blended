package blended.security.internal

import blended.security.BlendedPermissionManager
import blended.security.boot.GroupPrincipal
import javax.security.auth.Subject
import scala.collection.JavaConverters._

class GroupPermissionManager extends BlendedPermissionManager {

  override def permissions(subject: Subject): Seq[String] =
    subject.getPrincipals(classOf[GroupPrincipal]).asScala.map(_.getName()).toSeq
}
