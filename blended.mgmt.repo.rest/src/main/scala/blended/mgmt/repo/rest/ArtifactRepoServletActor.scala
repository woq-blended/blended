package blended.mgmt.repo.rest

import akka.actor.Actor
import akka.actor.ActorRefFactory
import akka.actor.Props
import blended.akka.OSGIActor
import blended.akka.OSGIActorConfig
import blended.mgmt.repo.ArtifactRepo
import blended.spray.SprayOSGIBridge
import blended.spray.SprayOSGIServlet
import javax.servlet.Servlet
import spray.http.Uri
import spray.routing.ExceptionHandler
import spray.routing.HttpService
import spray.routing.RejectionHandler
import spray.routing.Route
import spray.routing.RoutingSettings
import spray.servlet.ConnectorSettings
import spray.util.LoggingContext

/**
 * Actor implementing a HttpService and providing a Servlet into the OSGi registry, once started.
 *
 * @constructor
 *
 * Please use [[ArtifactRepoServletActor$#props]] to create instances of this actor.
 *
 */
class ArtifactRepoServletActor(
  cfg: OSGIActorConfig,
  override val artifactRepo: ArtifactRepo,
  contextPath: Option[String])
    extends OSGIActor(cfg)
    with HttpService { _: ArtifactRepoRoutes =>

  override def actorRefFactory: ActorRefFactory = context

  override def preStart(): Unit = {
    log.debug("About to preStart actor: {}", self)

    val repoContextPath = contextPath.getOrElse("") + "/" + artifactRepo.repoId

    val config = cfg.idSvc.getContainerContext().getContainerConfig()
    val connSettings = ConnectorSettings(config).copy(rootPath = Uri.Path(repoContextPath))
    implicit val routingSettings = RoutingSettings(config)
    implicit val routeLogger = LoggingContext.fromAdapter(log)
    implicit val exceptionHandler = ExceptionHandler.default
    implicit val rejectionHandler = RejectionHandler.Default

    val actorSys = context.system
    val routingActor = self

    val servlet = new SprayOSGIServlet with SprayOSGIBridge {
      override def routeActor = routingActor
      override def connectorSettings = connSettings
      override def actorSystem = actorSys
    }

    servlet.providesService[Servlet](
      "urlPatterns" -> "/",
      "Webapp-Context" -> repoContextPath,
      "Web-ContextPath" -> repoContextPath
    )
  }

  override def receive: Actor.Receive = runRoute(route)

}

object ArtifactRepoServletActor {

  /**
   * Actor creator for [[ArtifactRepoServletActor]].
   */
  def props(cfg: OSGIActorConfig, artifactRepo: ArtifactRepo, contextPath: Option[String]): Props =
    Props(new ArtifactRepoServletActor(cfg, artifactRepo, contextPath) with ArtifactRepoRoutes)

}