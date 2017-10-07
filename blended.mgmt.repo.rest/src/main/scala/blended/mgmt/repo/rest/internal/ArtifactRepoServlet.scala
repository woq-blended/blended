package blended.mgmt.repo.rest.internal

import akka.actor.{ ActorRef, ActorRefFactory, Props }
import blended.akka.OSGIActorConfig
import blended.mgmt.repo.ArtifactRepo
import blended.spray.{ BlendedHttpActor, BlendedHttpRoute, SprayOSGIServlet }
import domino.service_watching.ServiceWatcherEvent.{ AddingService, ModifiedService, RemovedService }
import org.slf4j.LoggerFactory
import spray.routing.Route
import blended.security.spray.ShiroBlendedSecuredRoute
import org.apache.shiro.subject.Subject
import spray.routing.Directive0
import spray.routing.Directive1

class ArtifactRepoServlet
    extends SprayOSGIServlet
    with BlendedHttpRoute
    with ShiroBlendedSecuredRoute
    with ArtifactRepoRoutes { self =>

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoServlet])

  protected var artifactRepos: List[ArtifactRepo] = List()

  override def startSpray(): Unit = {

    def addRepo(repo: ArtifactRepo): Unit = {
      log.debug("Registering artifactRepo: {}", repo)
      artifactRepos = repo :: artifactRepos
      log.debug("known repos: {}", artifactRepos)
    }

    def removeRepo(repo: ArtifactRepo): Unit = {
      log.debug("Registering artifactRepo: {}", repo)
      artifactRepos = artifactRepos.filter(r => r.repoId != repo.repoId)
      log.debug("known repos: {}", artifactRepos)
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

    super.createServletActor()
  }

}
