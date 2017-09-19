package blended.security.spray

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.mgt.SecurityManager
import org.apache.shiro.subject.Subject
import org.slf4j.LoggerFactory

import blended.spray.BlendedHttpRoute
import domino.service_consuming.ServiceConsuming
import org.apache.shiro.authc.AuthenticationException
import spray.routing.Directive.pimpApply
import spray.routing.Directive0
import spray.routing.Directive1
import spray.routing.authentication.BasicAuth
import spray.routing.authentication.UserPass
import spray.routing.directives.AuthMagnet.fromContextAuthenticator

trait ShiroBlendedSecuredRoute extends BlendedSecuredRoute with ServiceConsuming { this: BlendedHttpRoute =>

  private[this] lazy val log = LoggerFactory.getLogger(classOf[ShiroBlendedSecuredRoute])

  def myUserPassAuthenticator(userPass: Option[UserPass]): Future[Option[Subject]] =
    Future {
      userPass match {
        case Some(UserPass(un, up)) =>
          withService[SecurityManager, Option[Subject]] {
            case Some(secMgr) =>
              val subject = new Subject.Builder(secMgr).buildSubject()
              val token = new UsernamePasswordToken(un, up)
              try {
                subject.login(token)
                Some(subject)
              } catch {
                case e: AuthenticationException =>
                  log.error("shiro login failed for: {}", Array(subject.getPrincipal(), e): _*)
                  None
              }
            case _ =>
              log.error("No security manager found in osgi regsitry")
              None
          }
        case _ => None
      }
    }

  override protected def authenticated: Directive1[Subject] = authenticate(BasicAuth(myUserPassAuthenticator _, realm = "blended"))

  override protected def requirePermission(permission: String): Directive0 = mapInnerRoute { inner =>
    authenticated { subject =>
      log.info("subject: {} with principal: {}", Array(subject, Option(subject).map(_.getPrincipal()).getOrElse("null")): _*)
      log.debug("checking required permission: {}", permission)
      authorize(subject.isPermitted(permission)) {
        log.info("subject/principal: {} has required permissions: {}", Array(Option(subject).map(_.getPrincipal()).getOrElse(subject), permission): _*)
        inner
      }
    }
  }

}
