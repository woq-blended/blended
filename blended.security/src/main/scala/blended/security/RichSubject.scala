package blended.security

import blended.security.boot.{GroupPrincipal, UserPrincipal}

import scala.collection.JavaConverters.asScalaSetConverter
import javax.security.auth.Subject

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
