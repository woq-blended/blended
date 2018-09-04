package blended.security

import blended.security.boot.{GroupPrincipal, UserPrincipal}

import scala.collection.JavaConverters.asScalaSetConverter
import javax.security.auth.Subject

trait RichSubject {

  implicit class EnhancedSubject(subj: Subject) {

    def getPrincipal() : String = {
      subj.getPrincipals(classOf[UserPrincipal]).asScala.head.getName()
    }

    def getGroups() : List[String] = {
      subj.getPrincipals(classOf[GroupPrincipal]).asScala.toList.map(_.getName())
    }

    def isPermitted(mgr: BlendedPermissionManager, permission: BlendedPermission) : Boolean = {
      mgr.permissions(subj).granted.exists { p => p.allows(permission) }
    }
  }

}
