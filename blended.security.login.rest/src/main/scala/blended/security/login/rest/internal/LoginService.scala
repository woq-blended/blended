package blended.security.login.rest.internal

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

class LoginService {

  def route : Route = httpRoute

  private[this] lazy val httpRoute : Route = loginRoute ~ logoutRoute

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
