package blended.mgmt.rest.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.mgmt.repo.WritableArtifactRepo
import blended.persistence.PersistenceService
import blended.security.BlendedPermissionManager
import blended.updater.remote.RemoteUpdater
import blended.util.logging.Logger
import domino.DominoActivator
import domino.service_watching.ServiceWatcherEvent

class MgmtRestActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = Logger[MgmtRestActivator]

  whenBundleActive {
    whenServicesPresent[RemoteUpdater, PersistenceService, BlendedPermissionManager] {
      (updater, persistenceService, mgr) =>
        log.debug("Required Services present. Creating management collector service")

        val remoteContainerStatePersistor = new RemoteContainerStatePersistor(persistenceService)
        val version = bundleContext.getBundle().getVersion().toString()

        val collectorService = new CollectorServiceImpl(updater, remoteContainerStatePersistor, mgr, version)
        val route = collectorService.httpRoute
        log.debug("Registering Management REST API route under prefix: mgmt")
        SimpleHttpContext("mgmt", route).providesService[HttpContext]("prefix" -> "mgmt")

        // dynamically un/register repositories
        watchServices[WritableArtifactRepo] {
          case ServiceWatcherEvent.AddingService(service, context) =>
            val repoId = service.repoId
            log.debug(s"Adding repo: ${repoId}")
            collectorService.addArtifactRepo(service)
          case ServiceWatcherEvent.RemovedService(service, context) =>
            val repoId = service.repoId
            log.debug(s"Removing repo: ${repoId}")
            collectorService.removeArtifactRepo(service)
          case ServiceWatcherEvent.ModifiedService(service, context) =>
            val repoId = service.repoId
            log.debug(s"Modifying repo: ${repoId}")
            collectorService.removeArtifactRepo(service)
            collectorService.addArtifactRepo(service)
        }

        // TODO: remove; it looks like to be redundant to the above watch code
        // initially get all services of this type
        //        val repos = services[WritableArtifactRepo]
        //        log.debug(s"Initially starting with repos: ${repos}")
        //        repos.foreach { s => collectorService.addArtifactRepo(s) }

        whenActorSystemAvailable { cfg =>
          log.debug("Setting optional event stream to collector service")
          collectorService.setEventStream(Option(cfg.system.eventStream))
          onStop {
            log.debug("Unsetting optional event stream to collector service")
            collectorService.setEventStream(None)
          }
        }

    }

  }

}
