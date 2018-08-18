package blended.mgmt.repo.rest.internal

import blended.akka.http.HttpContext
import blended.akka.http.SimpleHttpContext
import blended.domino.TypesafeConfigWatching
import blended.mgmt.repo.ArtifactRepo
import blended.security.BlendedPermissionManager
import blended.util.logging.Logger
import domino.DominoActivator
import domino.service_watching.ServiceWatcherEvent.AddingService
import domino.service_watching.ServiceWatcherEvent.ModifiedService
import domino.service_watching.ServiceWatcherEvent.RemovedService

class ArtifactRepoRestActivator
  extends DominoActivator
  with TypesafeConfigWatching {

  private[this] val log = Logger[ArtifactRepoRestActivator]

  whenBundleActive {
    log.info(s"Activating bundle ${bundleContext.getBundle().getSymbolicName()}")
    whenServicePresent[BlendedPermissionManager] { mgr =>

      val repoRoutes = new ArtifactRepoRoutesImpl(mgr)
      
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
}
