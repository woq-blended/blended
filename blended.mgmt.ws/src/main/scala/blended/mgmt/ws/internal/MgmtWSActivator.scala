package blended.mgmt.ws.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.security.login.api.TokenStore
import domino.DominoActivator

class MgmtWSActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[TokenStore]{ store =>
        val wss = new MgmtWebSocketServer(cfg.system, store)
        SimpleHttpContext("mgmtws", wss.route).providesService[HttpContext]
      }
    }
  }
}
