package blended.akka.http.internal

import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import blended.akka.ActorSystemWatching
import domino.DominoActivator
import blended.util.config.Implicits._

class BlendedAkkaHttpActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {

    // reuse the blended akka system
    whenActorSystemAvailable { cfg =>

      val config = cfg.config
      
      val httpHost = config.getString("host", "0.0.0.0")
      val httpPort = config.getInt("port", 8080)

      //      val httpsHost = config.getString("ssl.host", "0.0.0.0")
      //      val httpsPort = config.getInt("ssl.port", 8443)

      implicit val actorSysten = cfg.system
      implicit val actorMaterializer = ActorMaterializer()
      // needed for the future flatMap/onComplete in the end
      implicit val executionContext = actorSysten.dispatcher

      val dynamicRoutes = new RouteProvider()

      log.info(s"Starting HTTP server at ${httpHost}:${httpPort}")
      val bindingFuture = Http().bindAndHandle(dynamicRoutes.dynamicRoute, httpHost, httpPort);
      bindingFuture.foreach { b =>
        log.info(s"Started HTTP server at ${b.localAddress}")
        // do we want to register the server into OSGi registry?
      }

      onStop {
        log.info(s"Stopping HTTP server at ${httpHost}:${httpPort}")
        bindingFuture.map(serverBinding => serverBinding.unbind())
      }

      // 
      dynamicRoutes.dynamicAdapt(capsuleContext, bundleContext)

    }
  }

}


