package blended.websocket.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.security.login.api.TokenStore
import domino.DominoActivator
import blended.util.config.Implicits._
import blended.util.logging.Logger

class WebSocketActivator extends DominoActivator with ActorSystemWatching {

  private val log : Logger = Logger[WebSocketActivator]

  whenBundleActive {
    whenActorSystemAvailable { cfg =>

      val webContext : String = cfg.config.getString("webcontext", "ws")

      whenServicePresent[TokenStore] { store =>
        val wss = new WebSocketProtocolHandler(cfg.system, store)
        log.info(s"Starting Blended Websocket protocol handler with context [$webContext]")
        SimpleHttpContext(webContext, wss.route).providesService[HttpContext]
      }
    }
  }
}
