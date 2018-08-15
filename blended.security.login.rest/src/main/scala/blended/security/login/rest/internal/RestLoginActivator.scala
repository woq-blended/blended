package blended.security.login.rest.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.security.login.api.TokenStore
import domino.DominoActivator

class RestLoginActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>
      whenServicePresent[TokenStore] { store =>
        val svc = new LoginService(store)(osgiCfg.system.dispatcher)
        SimpleHttpContext("login", svc.route).providesService[HttpContext]
      }
    }
  }
}
