package blended.security.internal

import blended.security.{GroupPrincipal, UserPrincipal}
import javax.security.auth.Subject

import scala.collection.JavaConverters._

trait RichSubject {

  implicit class EnhancedSubject(subj: Subject) {

    def getPrincipal() : String = {
      subj.getPrincipals().asScala.filter(_.isInstanceOf[UserPrincipal]).head.getName()
    }

    def getGroups() : List[String] = {
      subj.getPrincipals().asScala.filter(_.isInstanceOf[GroupPrincipal]).map(_.getName()).toList
    }

    def isPermitted(permission: String) : Boolean = {
      // TODO: Map permission evaluation
      true
    }
  }

}
