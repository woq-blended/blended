package blended.mgmt.repo.rest.internal

import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.domino.TypesafeConfigWatching
import blended.mgmt.repo.ArtifactRepo
import blended.security.BlendedPermissionManager
import blended.util.logging.Logger
import domino.DominoActivator
import domino.service_watching.ServiceWatcherEvent.{AddingService, ModifiedService, RemovedService}

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
      SimpleHttpContext("repo", repoRoutes.httpRoute).providesService[HttpContext]

      watchServices[ArtifactRepo] {
        case AddingService(repo, _) =>
          repoRoutes.addRepo(repo)
        case ModifiedService(repo, _) =>
          repoRoutes.removeRepo(repo)
          repoRoutes.addRepo(repo)
        case RemovedService(repo, _) =>
          repoRoutes.removeRepo(repo)
      }

    }

  }
}
