package blended.akka.http.internal

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatchers, Route}
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.util.logging.Logger
import domino.capsule.{Capsule, CapsuleContext}
import domino.service_consuming.ServiceConsuming
import domino.service_watching.{ServiceWatcherEvent, ServiceWatching}
import org.osgi.framework.{BundleContext, ServiceReference}

class RouteProvider(jmxSupport : Option[AkkaHttpServerJmxSupport]) {

  private[this] val log = Logger[RouteProvider]

  val initialRoute : Route = path("about") {
    get {
      complete("Blended Akka Http Server")
    }
  }

  @volatile
  private[this] var currentRoute : Route = initialRoute
  private[this] var contexts : Seq[HttpContext] = Seq()

  // We use the fact that route is just a function, so we can change dynamically
  private[this] val fixedDynamicRoute : Route = ctx => currentRoute(ctx)
  // We want a def in the API, but use a val internally
  def dynamicRoute : Route = fixedDynamicRoute

  private[this] def updateRoutes() : Unit = {
    log.debug("Current http contexts prefixes: " + contexts.map(_.prefix).mkString(", "))
    currentRoute = contexts.foldLeft(initialRoute) { (route, context) =>
      route ~ pathPrefix(PathMatchers.separateOnSlashes(context.prefix)) {
        context.route
      }
    }

    jmxSupport.foreach{ s => 
      s.updateInJmx{ info => info.copy(routes = contexts.map(_.prefix).toArray) }
    }
  }

  def dynamicAdapt(capsuleContext : CapsuleContext, bundleContext : BundleContext) : Unit = {

    def addContext(httpContext : HttpContext) : Unit = {
      log.info(s"Adding http context: ${httpContext}")
      // we currently allow only one route for each prefix
      contexts = contexts.filter(c => c.prefix != httpContext.prefix)
      contexts :+= httpContext
      updateRoutes()
    }

    def modifyContext(httpContext : HttpContext) : Unit = addContext(httpContext)

    def removeContext(httpContext : HttpContext) : Unit = {
      // we currently allow only one route for each prefix
      contexts = contexts.filter(c => c.prefix != httpContext.prefix)
      updateRoutes()
    }

    class WatchCapsule(
      override protected val capsuleContext : CapsuleContext,
      override protected val bundleContext : BundleContext
    )
      extends Capsule
      with ServiceWatching
      with ServiceConsuming {

      override def start() : Unit = {
        // We listen for supported services and add them to the route
        // wait for context registrations, and add them to the main route
        log.info("Listening for HttpContext registrations...")
        watchServices[HttpContext] {
          case ServiceWatcherEvent.AddingService(httpContext, watchContext)   => addContext(httpContext)
          case ServiceWatcherEvent.ModifiedService(httpContext, watchContext) => modifyContext(httpContext)
          case ServiceWatcherEvent.RemovedService(httpContext, watchContext)  => removeContext(httpContext)
        }

        def toContext[S <: AnyRef](route : Route, ref : ServiceReference[S]) : Option[HttpContext] = {
          Option(ref.getProperty("context")) match {
            case Some(prefix : String) if !prefix.trim().isEmpty() =>
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
          case ServiceWatcherEvent.AddingService(route, watchContext)   => toContext(route, watchContext.ref).foreach(addContext)
          case ServiceWatcherEvent.ModifiedService(route, watchContext) => toContext(route, watchContext.ref).foreach(modifyContext)
          case ServiceWatcherEvent.RemovedService(route, watchContext)  => toContext(route, watchContext.ref).foreach(removeContext)
        }

      }
      override def stop() : Unit = {
        // We unregister all routes
        contexts = Seq()
        updateRoutes()
      }
    }

    val capsule = new WatchCapsule(capsuleContext, bundleContext)
    capsuleContext.addCapsule(capsule)
  }

}
