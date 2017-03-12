package blended.spray

import akka.actor.{ActorLogging, ActorRefFactory, Props}
import blended.akka.{OSGIActor, OSGIActorConfig}
import spray.routing.{ExceptionHandler, HttpService, RejectionHandler, RoutingSettings}
import spray.util.LoggingContext

object BlendedHttpActor {

  def props(cfg: OSGIActorConfig, svc: BlendedHttpRoute, contextPath: String) : Props = Props(new BlendedHttpActor(cfg, svc, contextPath))
}

class BlendedHttpActor(cfg: OSGIActorConfig, svc: BlendedHttpRoute, contextPath: String) extends OSGIActor(cfg) with ActorLogging with HttpService {

  val symbolicName = cfg.bundleContext.getBundle().getSymbolicName()
  log.info(s"Initialising Spray actor for [${symbolicName}], using servlet context path [$contextPath]")

  val bundleConfig = cfg.system.settings.config.withValue(symbolicName, cfg.system.settings.config.root())

  override implicit def actorRefFactory: ActorRefFactory = context

  implicit val routingSettings = RoutingSettings(bundleConfig)
  implicit val routeLogger = LoggingContext.fromAdapter(log)
  implicit val exceptionHandler = ExceptionHandler.default
  implicit val rejectionHandler = RejectionHandler.Default

  def receive = runRoute(svc.httpRoute)

}
