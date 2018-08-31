package blended.security.login.rest.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.security.BlendedPermissionManager
import blended.security.login.api.TokenStore
import blended.util.logging.Logger
import domino.DominoActivator

class RestLoginActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = Logger[RestLoginActivator]
  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>
      whenServicesPresent[TokenStore, BlendedPermissionManager] { (store, mgr) =>
        log.info("Starting login REST service")
        val svc = new LoginService(store, mgr)(osgiCfg.system.dispatcher)
        SimpleHttpContext("login", svc.route).providesService[HttpContext]
      }
    }
  }
}
