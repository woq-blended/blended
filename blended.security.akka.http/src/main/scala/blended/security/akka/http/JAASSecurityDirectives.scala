package blended.security.akka.http

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges, HttpCredentials}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.{AuthenticationDirective, AuthenticationResult}
import blended.security.{BlendedPermission, BlendedPermissionManager}
import blended.security.SubjectImplicits._
import blended.util.logging.Logger
import javax.security.auth.Subject
import javax.security.auth.callback._
import javax.security.auth.login.LoginContext

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * JAAS Based BlendedSecurityDirectives.
 *
 */
trait JAASSecurityDirectives extends BlendedSecurityDirectives {

  val mgr : BlendedPermissionManager

  private[this] lazy val log = Logger[JAASSecurityDirectives]

  private[this] val challenge = HttpChallenges.basic("blended")

  private[this] def auth(creds: BasicHttpCredentials): Option[Subject] = {

    val cbHandler = new CallbackHandler {
      override def handle(callbacks: Array[Callback]): Unit = {
        callbacks.foreach { cb: Callback =>
          cb match {
            case nameCallback: NameCallback => nameCallback.setName(creds.username)
            case pwdCallback: PasswordCallback => pwdCallback.setPassword(creds.password.toCharArray())
            case other => throw new UnsupportedCallbackException(other, "The submitted callback is not supported")
          }
        }
      }
    }

    val lc : LoginContext = new LoginContext("blended", cbHandler)

    try {
      lc.login()
      Some(lc.getSubject())
    } catch {
      case t: Throwable =>
        log.error(t)(s"Login failed for [${creds.username}]")
        None
    }
  }

  private[this] def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[AuthenticationResult[Subject]] =
    Future {
      credentials match {
        case Some(creds: BasicHttpCredentials) => auth(creds) match {
          case Some(s) => AuthenticationResult.success(s)
          case None => AuthenticationResult.failWithChallenge(challenge)
        }
        case _ => AuthenticationResult.failWithChallenge(challenge)
      }
    }

  override val authenticated  : AuthenticationDirective[Subject] = authenticateOrRejectWithChallenge(myUserPassAuthenticator _)

  override def requirePermission(permission: BlendedPermission) : Directive0 = mapInnerRoute { inner =>
    authenticated { subject =>
      log.info(s"subject: ${subject} with principal: ${Option(subject).map(_.getPrincipal()).getOrElse("null")}")
      log.debug(s"checking required permission: ${permission}")
      authorize(subject.isPermitted(mgr, permission)) {
        log.info(s"subject/principal: ${Option(subject).map(_.getPrincipal()).getOrElse(subject)} has required permissions: ${permission}")
        inner
      }
    }
  }

  override def requireGroup(group: String): Directive0 = mapInnerRoute { inner =>
    authenticated { subject =>
      log.info(s"subject: ${subject} with principal: ${Option(subject).map(_.getPrincipal()).getOrElse("null")}")
      log.debug(s"checking required group: ${group}")
      authorize(subject.getGroups().contains(group)) {
        log.info(s"subject/principal: ${Option(subject).map(_.getPrincipal()).getOrElse(subject)} has required group: ${group}")
        inner
      }
    }
  }
}


