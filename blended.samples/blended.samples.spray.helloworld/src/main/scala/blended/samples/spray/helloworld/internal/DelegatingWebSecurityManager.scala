package blended.samples.spray.helloworld.internal

import org.apache.shiro.mgt.SecurityManager
import org.apache.shiro.web.mgt.WebSecurityManager
import org.apache.shiro.authc.AuthenticationToken
import org.apache.shiro.authc.AuthenticationInfo
import org.apache.shiro.subject.PrincipalCollection
import org.apache.shiro.authz.Permission
import org.apache.shiro.authc.Authenticator
import org.apache.shiro.authz.Authorizer
import scala.annotation.varargs
import org.apache.shiro.session.mgt.SessionManager
import org.apache.shiro.session.mgt.SessionContext
import org.apache.shiro.session.Session
import org.apache.shiro.subject.Subject
import org.apache.shiro.session.mgt.SessionKey
import org.apache.shiro.subject.SubjectContext
import org.slf4j.LoggerFactory

class DelegatingWebSecurityManager(underlying: SecurityManager)
    extends WebSecurityManager
    with SecurityManager
    with Authenticator
    with Authorizer
    with SessionManager {

  require(underlying != null, "Underlying security manager must not be null.")

  LoggerFactory.getLogger(classOf[DelegatingWebSecurityManager]).
    debug("Delegating all method calls to underlying security manager: {}", underlying)

  // org.apache.shiro.web.mgt.WebSecurityManager

  override def isHttpSessionMode(): Boolean = true

  //  org.apache.shiro.mgt.SecurityManager

  override def createSubject(context: SubjectContext): Subject =
    underlying.createSubject(context)

  override def login(subject: Subject, authenticationToken: AuthenticationToken): Subject =
    underlying.login(subject, authenticationToken)

  override def logout(subject: Subject): Unit =
    underlying.logout(subject)

  // org.apache.shiro.authc.Authenticator

  override def authenticate(authenticationToken: AuthenticationToken): AuthenticationInfo =
    underlying.authenticate(authenticationToken)

  // org.apache.shiro.authz.Authorizer

  override def checkPermission(subjectPrincipal: PrincipalCollection, permission: Permission): Unit =
    underlying.checkPermission(subjectPrincipal, permission)

  override def checkPermission(subjectPrincipal: PrincipalCollection, permission: String): Unit =
    underlying.checkPermission(subjectPrincipal, permission)

  override def checkPermissions(subjectPrincipal: PrincipalCollection, permissions: java.util.Collection[Permission]): Unit =
    underlying.checkPermissions(subjectPrincipal, permissions)

  //  @varargs
  override def checkPermissions(subjectPrincipal: PrincipalCollection, permissions: String*): Unit =
    underlying.checkPermissions(subjectPrincipal, permissions: _*)

  override def checkRole(subjectPrincipal: PrincipalCollection, roleIdentifier: String): Unit =
    underlying.checkRole(subjectPrincipal, roleIdentifier)

  //  @varargs
  override def checkRoles(subjectPrincipal: PrincipalCollection, roleIdentifiers: String*): Unit =
    underlying.checkRoles(subjectPrincipal, roleIdentifiers: _*)

  override def checkRoles(subjectPrincipal: PrincipalCollection, roleIdentifiers: java.util.Collection[String]): Unit =
    underlying.checkRoles(subjectPrincipal, roleIdentifiers)

  override def hasRole(subjectPrincipal: PrincipalCollection, roleIdentifier: String): Boolean =
    underlying.hasRole(subjectPrincipal, roleIdentifier)

  override def hasRoles(subjectPrincipal: PrincipalCollection, roleIdentifiers: java.util.List[String]): Array[Boolean] =
    underlying.hasRoles(subjectPrincipal, roleIdentifiers)

  override def hasAllRoles(subjectPrincipal: PrincipalCollection, roleIdentifiers: java.util.Collection[String]): Boolean =
    underlying.hasAllRoles(subjectPrincipal, roleIdentifiers)

  override def isPermitted(subjectPrincipal: PrincipalCollection, permissions: java.util.List[Permission]): Array[Boolean] =
    underlying.isPermitted(subjectPrincipal, permissions)

  override def isPermitted(subjectPrincipal: PrincipalCollection, permission: Permission): Boolean =
    underlying.isPermitted(subjectPrincipal, permission)

  //  @varargs
  override def isPermitted(subjectPrincipal: PrincipalCollection, permissions: String*): Array[Boolean] =
    underlying.isPermitted(subjectPrincipal, permissions: _*)

  override def isPermitted(subjectPrincipal: PrincipalCollection, permission: String): Boolean =
    underlying.isPermitted(subjectPrincipal, permission)

  override def isPermittedAll(subjectPrincipal: PrincipalCollection, permissions: java.util.Collection[Permission]): Boolean =
    underlying.isPermittedAll(subjectPrincipal, permissions)

  //  @varargs
  override def isPermittedAll(subjectPrincipal: PrincipalCollection, permissions: String*): Boolean =
    underlying.isPermittedAll(subjectPrincipal, permissions: _*)

  // org.apache.shiro.session.mgt.SessionManager

  override def start(context: SessionContext): Session = underlying.start(context)

  override def getSession(key: SessionKey): Session = underlying.getSession(key)

}