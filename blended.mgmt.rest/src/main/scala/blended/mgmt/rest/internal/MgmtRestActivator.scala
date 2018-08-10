package blended.mgmt.rest.internal

import blended.akka.ActorSystemWatching
import blended.akka.http.{ HttpContext, SimpleHttpContext }
import blended.persistence.PersistenceService
import blended.updater.remote.RemoteUpdater
import blended.util.logging.Logger
import domino.DominoActivator

class MgmtRestActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = Logger[MgmtRestActivator]

  whenBundleActive {
    whenServicesPresent[RemoteUpdater, PersistenceService] {
      (updater, persistenceService) =>
        log.debug("Required Services present. Creating management collector service")

        val remoteContainerStatePersistor = new RemoteContainerStatePersistor(persistenceService)
        val version = bundleContext.getBundle().getVersion().toString()

        val collectorService = new CollectorServiceImpl(updater, remoteContainerStatePersistor, version)
        val route = collectorService.httpRoute
        log.debug("Registering Management REST API route under prefix: mgmt")
        SimpleHttpContext("mgmt", route).providesService[HttpContext]

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