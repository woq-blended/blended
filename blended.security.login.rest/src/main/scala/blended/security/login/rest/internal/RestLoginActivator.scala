package blended.security.login.rest.internal

import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.security.BlendedPermissionManager
import domino.DominoActivator

class RestLoginActivator extends DominoActivator {

  whenBundleActive {
    whenServicePresent[BlendedPermissionManager]{ mgr =>
      val svc = new LoginService(mgr)
      SimpleHttpContext("login", svc.route).providesService[HttpContext]
    }
  }
}
