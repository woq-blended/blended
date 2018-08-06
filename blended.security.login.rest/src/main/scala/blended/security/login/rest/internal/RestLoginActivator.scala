package blended.security.login.rest.internal

import blended.akka.http.{HttpContext, SimpleHttpContext}
import domino.DominoActivator

class RestLoginActivator extends DominoActivator {

  whenBundleActive {
    val svc = new LoginService()
    SimpleHttpContext("login", svc.route).providesService[HttpContext]
  }
}
