package blended.mgmt.ws.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.jmx.BlendedMBeanServerFacade
import blended.security.login.api.TokenStore
import domino.DominoActivator

class MgmtWSActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[TokenStore] { store =>
        whenServicePresent[BlendedMBeanServerFacade]{ facade =>
          val wss = new MgmtWebSocketServer(cfg.system, store, facade)
          SimpleHttpContext("mgmtws", wss.route).providesService[HttpContext]
        }
      }
    }
  }
}
