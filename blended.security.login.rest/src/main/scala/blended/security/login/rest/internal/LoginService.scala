package blended.security.login.rest.internal

import blended.spray.BlendedHttpRoute
import spray.http.{HttpRequest, HttpResponse, StatusCodes}
import spray.routing.Route

trait LoginService extends BlendedHttpRoute {

  override val httpRoute : Route = loginRoute

  def loginRoute : Route = {
    path("login") {
      post {
        entity(as[HttpRequest]) { request =>
          complete(HttpResponse(StatusCodes.NotImplemented))
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
