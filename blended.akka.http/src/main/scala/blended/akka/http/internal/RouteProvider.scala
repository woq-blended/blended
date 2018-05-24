package blended.akka.http.internal

import domino.capsule.Capsule
import domino.service_watching.ServiceWatching
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import blended.akka.http.HttpContext
import domino.capsule.CapsuleContext
import org.osgi.framework.BundleContext
import domino.service_watching.ServiceWatcherEvent
import domino.service_watching.ServiceWatcherContext
import domino.service_consuming.ServiceConsuming
import blended.akka.http.SimpleHttpContext
import org.osgi.framework.ServiceReference
import akka.http.scaladsl.server.PathMatchers

class RouteProvider {

  private[this] val log = org.log4s.getLogger

  val initialRoute: Route = path("about") {
    get {
      complete("Blended Akka Http Server")
    }
  }

  @volatile
  private[this] var currentRoute: Route = initialRoute
  private[this] var contexts: Seq[HttpContext] = Seq()

  // We use the fact that route is just a function, so we can change dynamically
  private[this] val fixedDynamicRoute: Route = ctx => currentRoute(ctx)
  // We want a def in the API, but use a val internally
  def dynamicRoute: Route = fixedDynamicRoute

  private[this] def updateRoutes(): Unit = {
    log.debug("Current http contexts prefixes: " + contexts.map(_.prefix).mkString(", "))
    currentRoute = contexts.foldLeft(initialRoute) { (route, context) =>
      route ~ pathPrefix(PathMatchers.separateOnSlashes(context.prefix)) {
        context.route
      }
    }
  }

  def dynamicAdapt(capsuleContext: CapsuleContext, bundleContext: BundleContext): Unit = {

    def addContext(httpContext: HttpContext): Unit = {
      log.info(s"Adding http context: ${httpContext}")
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

    class WatchCapsule(
      override protected val capsuleContext: CapsuleContext,
      override protected val bundleContext: BundleContext
    )
      extends Capsule
      with ServiceWatching
      with ServiceConsuming {

      override def start(): Unit = {
        // We listen for supported services and add them to the route
        // wait for context registrations, and add them to the main route
        log.info("Listening for HttpContext registrations...")
        watchServices[HttpContext] {
          case ServiceWatcherEvent.AddingService(httpContext, watchContext) => addContext(httpContext)
          case ServiceWatcherEvent.ModifiedService(httpContext, watchContext) => modifyContext(httpContext)
          case ServiceWatcherEvent.RemovedService(httpContext, watchContext) => removeContext(httpContext)
        }

        def toContext[S <: AnyRef](route: Route, ref: ServiceReference[S]): Option[HttpContext] = {
          Option(ref.getProperty("context")) match {
            case Some(prefix: String) if !prefix.trim().isEmpty() =>
              val ctx = SimpleHttpContext(prefix, route)
              log.debug(s"Wrapped Route with prefix-property to HttpContext: ${ctx}")
              Some(ctx)
            case _ =>
              log.warn(s"Missing or unsupported property 'prefix' defined for service reference: ${ref}. Skipping registration.")
              None

          }
        }

        log.info("Listening for Route registrations...")
        watchServices[Route] {
          case ServiceWatcherEvent.AddingService(route, watchContext) => toContext(route, watchContext.ref).foreach(addContext)
          case ServiceWatcherEvent.ModifiedService(route, watchContext) => toContext(route, watchContext.ref).foreach(modifyContext)
          case ServiceWatcherEvent.RemovedService(route, watchContext) => toContext(route, watchContext.ref).foreach(removeContext)
        }

        // Use all contexts that were registered before we came, too
        log.debug("Registering already present HttpContext's and Route's")
        services[HttpContext].foreach(addContext)
        serviceRefs[Route].flatMap(r => toContext(bundleContext.getService(r), r)).foreach(addContext)

      }
      override def stop(): Unit = {
        // We unregister all routes
        contexts = Seq()
        updateRoutes()
      }
    }

    val capsule = new WatchCapsule(capsuleContext, bundleContext)
    capsuleContext.addCapsule(capsule)
  }

}