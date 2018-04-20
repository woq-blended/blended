package blended.security

import java.nio.file.attribute.GroupPrincipal

import blended.security.boot.UserPrincipal
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
