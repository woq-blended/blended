package blended.mgmt.repo.rest.internal

import domino.DominoActivator
import blended.domino.TypesafeConfigWatching
import blended.akka.http.SimpleHttpContext
import blended.akka.http.HttpContext
import blended.mgmt.repo.ArtifactRepo
import domino.service_watching.ServiceWatcherEvent.ModifiedService
import domino.service_watching.ServiceWatcherEvent.AddingService
import domino.service_watching.ServiceWatcherEvent.RemovedService

class ArtifactRepoRestActivator
  extends DominoActivator
  with TypesafeConfigWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    log.info(s"Activating bundle ${bundleContext.getBundle().getSymbolicName()}")

    val repoRoutes = new ArtifactRepoRoutesImpl()
    onStop {
      repoRoutes.clearRepos()
    }

    log.info("Registering route under context path: repo")
    new SimpleHttpContext("repo", repoRoutes.httpRoute).providesService[HttpContext]

    watchServices[ArtifactRepo] {
      case AddingService(repo, r) =>
        repoRoutes.addRepo(repo)
      case ModifiedService(repo, r) =>
      // nothing to do
      case RemovedService(repo, r) =>
        repoRoutes.removeRepo(repo)
    }
  }
}