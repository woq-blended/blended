package blended.websocket.internal

import akka.actor.{ActorRef, ActorSystem, Props}
import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.jmx.BlendedMBeanServerFacade
import blended.security.login.api.TokenStore
import blended.util.config.Implicits._
import blended.util.logging.Logger
import blended.websocket.WebSocketCommandPackage
import domino.DominoActivator
import domino.service_watching.ServiceWatcherEvent.{AddingService, ModifiedService, RemovedService}

class WebSocketActivator extends DominoActivator with ActorSystemWatching {

  private val log : Logger = Logger[WebSocketActivator]

  whenBundleActive {
    whenActorSystemAvailable { cfg =>

      implicit val system : ActorSystem = cfg.system

      val webContext : String = cfg.config.getString("webcontext", "ws")

      whenServicePresent[TokenStore] { store =>
        val wss = new WebSocketProtocolHandler(cfg.system, store)

        watchServices[WebSocketCommandPackage] {
          case AddingService(pkg, _) => wss.addCommandPackage(pkg)

          case RemovedService(pkg, _) => wss.removeCommandPackage(pkg)

          case ModifiedService(pkg, _) =>
            wss.removeCommandPackage(pkg)
            wss.addCommandPackage(pkg)
        }



        log.info(s"Starting Blended Websocket protocol handler with context [$webContext]")
        SimpleHttpContext(webContext, wss.route).providesService[HttpContext]
      }

      val actor : ActorRef = system.actorOf(Props[WebSocketSubscriptionManager])

      onStop{
        system.stop(actor)
      }

      whenServicePresent[BlendedMBeanServerFacade] { facade =>
        val jmxPackage : WebSocketCommandPackage = new JmxCommandPackage(jmxFacade = facade)
        log.info(s"Starting WebSocket command package [${jmxPackage.namespace}]")
        jmxPackage.providesService[WebSocketCommandPackage](
          "namespace" -> jmxPackage.namespace
        )
      }

      val blendedHandler : WebSocketCommandPackage = new BlendedCommandPackage()
      log.info(s"Starting WebSocket command package [${blendedHandler.namespace}]")
      blendedHandler.providesService[WebSocketCommandPackage](
        "namespace" -> blendedHandler.namespace
      )

    }
  }
}
