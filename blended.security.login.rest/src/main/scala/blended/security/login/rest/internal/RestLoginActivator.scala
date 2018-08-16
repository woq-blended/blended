package blended.security.login.rest.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.security.BlendedPermissionManager
import blended.security.login.api.TokenStore
import domino.DominoActivator

class RestLoginActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>
      whenServicesPresent[TokenStore, BlendedPermissionManager] { (store, mgr) =>
        val svc = new LoginService(store, mgr)(osgiCfg.system.dispatcher)
        SimpleHttpContext("login", svc.route).providesService[HttpContext]
      }
    }
  }
}
