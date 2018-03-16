package blended.security.akka.http

import akka.http.scaladsl.server.directives.Credentials
import domino.service_consuming.ServiceConsuming
import scala.concurrent.Future
import org.apache.shiro.subject.Subject
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.authc.AuthenticationException
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.shiro.mgt.SecurityManager
import org.osgi.framework.BundleContext
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.directives.AuthenticationResult
import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directive0

/**
 * Apache Shiro-bases BlendedSecurityDirectives.
 *
 * @constructor
 * Creates this class with the given SecurityManager provider function.
 */
class ShiroBlendedSecurityDirectives(securityManager: () => Option[SecurityManager]) extends BlendedSecurityDirectives {

  private[this] lazy val log = org.log4s.getLogger

  /**
   * Creates this class but retrieves the SecurityManager from the given BundleContext on demand.
   */
  def this(bundleContext: BundleContext) {
    this(() => {
      Option(bundleContext.getServiceReference(classOf[SecurityManager].getName())).
        flatMap(ref => Option(bundleContext.getService(ref))).
        collect { case s: SecurityManager => s }
    })
  }

  val challenge = HttpChallenges.basic("blended")

  def auth(creds: BasicHttpCredentials): Option[Subject] =
    securityManager() match {
      case Some(secMgr) =>
        val subject = new Subject.Builder(secMgr).buildSubject()
        val token = new UsernamePasswordToken(creds.username, creds.password)
        try {
          subject.login(token)
          Some(subject)
        } catch {
          case e: AuthenticationException =>
            log.error(e)(s"shiro login failed for: ${subject.getPrincipal()}")
            None
        }
      case _ =>
        log.error("No security manager, failing authentication")
        None
    }

  def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, Subject]] =
    Future {
      credentials match {
        case Some(creds: BasicHttpCredentials) => auth(creds).toRight(challenge)
        case _ => Left(challenge)
      }
    }

  override def authenticated: AuthenticationDirective[Subject] = authenticateOrRejectWithChallenge(myUserPassAuthenticator _)

  override def requirePermission(permission: String): Directive0 = mapInnerRoute { inner =>
    authenticated { subject =>
      log.info(s"subject: ${subject} with principal: ${Option(subject).map(_.getPrincipal()).getOrElse("null")}")
      log.debug(s"checking required permission: ${permission}")
      authorize(subject.isPermitted(permission)) {
        log.info(s"subject/principal: ${Option(subject).map(_.getPrincipal()).getOrElse(subject)} has required permissions: ${permission}")
        inner
      }
    }
  }

}


