package blended.security.akka.http

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge, HttpChallenges, HttpCredentials}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.AuthenticationDirective
import org.apache.shiro.authc.{AuthenticationException, UsernamePasswordToken}
import org.apache.shiro.mgt.SecurityManager
import org.apache.shiro.subject.Subject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Apache Shiro SecurityManager-bases BlendedSecurityDirectives.
 *
 */
trait ShiroBlendedSecurityDirectives extends BlendedSecurityDirectives {

  /**
   * External dependency to a security manager.
   */
  protected def securityManager(): Option[SecurityManager]

  private[this] lazy val log = org.log4s.getLogger

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


