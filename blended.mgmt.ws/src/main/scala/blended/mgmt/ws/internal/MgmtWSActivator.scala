package blended.mgmt.ws.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import domino.DominoActivator

class MgmtWSActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      val wss = new SimpleWebSocketServer(cfg.system)
      SimpleHttpContext("mgmtws", wss.route).providesService[HttpContext]
    }
  }
}
