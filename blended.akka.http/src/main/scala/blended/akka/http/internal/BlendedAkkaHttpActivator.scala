package blended.akka.http.internal

import akka.stream.ActorMaterializer
import blended.akka.ActorSystemWatching
import domino.DominoActivator
import domino.service_watching.ServiceWatcherEvent
//import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.collection.immutable.Seq
import blended.akka.http.HttpContext
import blended.akka.http.SimpleHttpContext
import domino.service_watching.ServiceWatcherContext

class BlendedAkkaHttpActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {

    // reuse the blended akka system
    whenActorSystemAvailable { cfg =>

      // TODO: make listening address and port configurable

      implicit val actorSysten = cfg.system
      implicit val actorMaterializer = ActorMaterializer()
      // needed for the future flatMap/onComplete in the end
      implicit val executionContext = actorSysten.dispatcher

      val dynamicRoutes = new RouteProvider()

      log.info("About to start HTTP server at 0.0.0.0:9991")
      val bindingFuture = Http().bindAndHandle(dynamicRoutes.dynamicRoute, "0.0.0.0", 9991);
      bindingFuture.foreach { b =>
        log.info(s"Started HTTP server at ${b.localAddress}")
        // do we want to register the server into OSGi registry?
      }

      onStop {
        bindingFuture.map(serverBinding => serverBinding.unbind())
      }
      
      dynamicRoutes.dynamicAdapt(capsuleContext, bundleContext)

    }
  }

}


