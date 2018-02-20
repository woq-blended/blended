package blended.akka.http.internal

import akka.stream.ActorMaterializer
import blended.akka.ActorSystemWatching
import domino.DominoActivator
import akka.http.scaladsl.Http
import blended.akka.http.HttpContext
import blended.akka.http.SimpleHttpContext
import domino.service_watching.ServiceWatcherContext

class BlendedAkkaHttpActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {

    // reuse the blended akka system
    whenActorSystemAvailable { cfg =>

      // Read the bundle config value for the given `key` or return the default, when the key is not present
      def getOrDefault[T](key: String, default: T): T = if (cfg.config.hasPath(key)) {
        default match {
          case x: String => cfg.config.getString(key).asInstanceOf[T]
          case x: Int => cfg.config.getInt(key).asInstanceOf[T]
          case x: Long => cfg.config.getLong(key).asInstanceOf[T]
        }
      } else default

      val httpHost = getOrDefault("host", "0.0.0.0")
      val httpPort = getOrDefault("port", 8080)

      //      val httpsHost = getOrDefault("ssl.host", "0.0.0.0")
      //      val httpsPort = getOrDefault("ssl.port", 8443)

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


