package blended.security.login.rest.internal

import blended.akka.http.{HttpContext, SimpleHttpContext}
import domino.DominoActivator

class LoginActivator extends DominoActivator {

  whenBundleActive {
    val svc = new LoginService()
    SimpleHttpContext("login", svc.route).providesService[HttpContext]
  }
}
