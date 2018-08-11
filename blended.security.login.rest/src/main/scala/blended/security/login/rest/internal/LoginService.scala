package blended.security.login.rest.internal

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import blended.security.akka.http.JAASSecurityDirectives
import blended.security.json.PrickleProtocol._
import blended.security.{BlendedPermissionManager, BlendedPermissions}
import javax.security.auth.Subject
import org.slf4j.LoggerFactory
import prickle.Pickle

class LoginService(permissionMgr: BlendedPermissionManager) extends JAASSecurityDirectives {

  private[this] val log = LoggerFactory.getLogger(classOf[LoginService])

  def route : Route = httpRoute

  private[this] lazy val httpRoute : Route = loginRoute ~ logoutRoute

  def loginRoute : Route = {
    pathSingleSlash {
      get {
        log.warn("Login must be executed with a HTTP Post")
        complete(HttpResponse(StatusCodes.Forbidden))
      }
      post {
        authenticated { user : Subject =>
          val permissions : BlendedPermissions = permissionMgr.permissions(user)
          complete(Pickle.intoString(permissions))
        }
      }
    }
  }

  def logoutRoute : Route = {
    path("/logout") {
      complete(HttpResponse(StatusCodes.NotImplemented))
    }
  }
}
