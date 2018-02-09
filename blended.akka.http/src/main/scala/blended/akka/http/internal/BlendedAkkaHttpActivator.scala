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

      implicit val actorSysten = cfg.system
      implicit val actorMaterializer = ActorMaterializer()
      // needed for the future flatMap/onComplete in the end
      implicit val executionContext = actorSysten.dispatcher

      val initialRoute: Route = path("about") {
        get {
          complete("Blended Akka Htttp Server")
        }
      }
      //      var currentRoute: Route = initialRoute

      @volatile var currentRoute: Route = initialRoute
      var contexts: Seq[HttpContext] = Seq()

      def updateRoutes(): Unit = {
        log.debug("Current http contexts prefixes: " + contexts.map(_.prefix).mkString(", "))
        currentRoute = contexts.foldLeft(initialRoute) { (route, context) =>
          route ~ pathPrefix(context.prefix) {
            context.route
          }
        }
      }

      // We use the fact that route is just a function, so we can change dynamically
      val route: Route = ctx => currentRoute(ctx)

      log.info("About to start HTTP server at 0.0.0.0:9991")
      val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 9991);
      bindingFuture.foreach { b =>
        log.info(s"Started HTTP server at ${b.localAddress}")
        // do we want to register the server into OSGi registry?
      }

      onStop {
        bindingFuture.map(serverBinding => serverBinding.unbind())
      }

      def addContext(httpContext: HttpContext): Unit = {
        // we currently allow only one route for each prefix
        contexts = contexts.filter(c => c.prefix != httpContext.prefix)
        contexts :+= httpContext
        updateRoutes()
      }

      def modifyContext(httpContext: HttpContext): Unit = addContext _

      def removeContext(httpContext: HttpContext): Unit = {
        // we currently allow only one route for each prefix
        contexts = contexts.filter(c => c.prefix != httpContext.prefix)
        updateRoutes()
      }

      // wait for context registrations, and add them to the main route
      watchServices[HttpContext] {
        case ServiceWatcherEvent.AddingService(httpContext, watchContext) => addContext(httpContext)
        case ServiceWatcherEvent.ModifiedService(httpContext, watchContext) => modifyContext(httpContext)
        case ServiceWatcherEvent.RemovedService(httpContext, watchContext) => removeContext(httpContext)
      }

      def toContext[S <: AnyRef](route: Route, ctx: ServiceWatcherContext[S]): Option[HttpContext] = {
        Option(ctx.ref.getProperty("context")).collect {
          case prefix: String if !prefix.trim().isEmpty() => SimpleHttpContext(prefix, route)
        }
      }

      watchServices[Route] {
        case ServiceWatcherEvent.AddingService(route, watchContext) => toContext(route, watchContext).foreach(addContext)
        case ServiceWatcherEvent.ModifiedService(route, watchContext) => toContext(route, watchContext).foreach(modifyContext)
        case ServiceWatcherEvent.RemovedService(route, watchContext) => toContext(route, watchContext).foreach(removeContext)
      }

      // Use all contexts that were registered before we came, too
      contexts ++= services[HttpContext]
      updateRoutes()

//      val demoRoute =
//        get {
//          pathEnd {
//            complete("Hello")
//          } ~
//            path(Segment) { name =>
//              complete(s"Hello $name")
//            }
//        }
//
//      SimpleHttpContext("hello", demoRoute).providesService[HttpContext]
    }
  }

}


