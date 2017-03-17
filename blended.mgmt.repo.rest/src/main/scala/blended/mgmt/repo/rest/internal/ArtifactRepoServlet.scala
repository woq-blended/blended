package blended.mgmt.repo.rest.internal

import akka.actor.{ActorRef, ActorRefFactory, Props}
import blended.akka.OSGIActorConfig
import blended.mgmt.repo.ArtifactRepo
import blended.spray.{BlendedHttpActor, BlendedHttpRoute, SprayOSGIServlet}
import domino.service_watching.ServiceWatcherEvent.{AddingService, ModifiedService, RemovedService}
import org.slf4j.LoggerFactory
import spray.routing.Route


class ArtifactRepoServlet extends SprayOSGIServlet with BlendedHttpRoute {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoServlet])

  var actors: Map[ArtifactRepo, ActorRef] = Map()

  override val httpRoute: Route = throw new Exception("Servlet Actor can't be created without a repo reference")

  def repoProps(repo: ArtifactRepo)(cfg: OSGIActorConfig, contextPath: String): Props =
    BlendedHttpActor.props(cfg, new ArtifactRepoRoutes {
      override def actorConfig: OSGIActorConfig = cfg
      override protected def artifactRepo: ArtifactRepo = repo
      override implicit def actorRefFactory: ActorRefFactory = cfg.system
    }, contextPath)

  override def startSpray(osgiCfg: OSGIActorConfig): Unit = {

    /**
      * create and start actor and add to state
      */
    def addRepo(repo: ArtifactRepo): Unit = {
      val repoContextPath = contextPath + "/" + repo.repoId
      val props = repoProps(repo)(osgiCfg, repoContextPath)
      val actorRef = createServletActor(props)
      log.info("Created actor {} for artifact repo {}", Array(actorRef, repo): _*)
      actors += repo -> actorRef
      log.debug("known repos and their actors: {}", actors)
    }

    /**
      *  stop actor and remove from state
      */
    def removeRepo(repo: ArtifactRepo): Unit = {
      actors.get(repo).map { actor =>
        log.info("About to stop actor {} for artifact repo {}", Array(actor, repo): _*)
        osgiCfg.system.stop(actor)
        actors -= repo
      }
      log.debug("known repos and their actors: {}", actors)
    }

    watchServices[ArtifactRepo] {
      case AddingService(repo, context) =>
        addRepo(repo)
      case ModifiedService(repo, context) =>
        removeRepo(repo)
        addRepo(repo)
      case RemovedService(repo, context) =>
        removeRepo(repo)
    }
  }
}
