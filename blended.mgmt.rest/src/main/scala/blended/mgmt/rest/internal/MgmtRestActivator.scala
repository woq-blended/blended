package blended.mgmt.rest.internal

import domino.DominoActivator
import akka.actor.ActorSystem
import blended.akka.ActorSystemWatching
import blended.updater.remote.RemoteUpdater
import blended.persistence.PersistenceService
import blended.security.akka.http.BlendedSecurityDirectives
import blended.security.akka.http.ShiroBlendedSecurityDirectives
import blended.prickle.akka.http.PrickleSupport
import sun.security.tools.policytool.AuthPerm
import blended.akka.http.SimpleHttpContext
import blended.akka.http.HttpContext
import org.apache.shiro.mgt.SecurityManager

class MgmtRestActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    whenServicesPresent[RemoteUpdater, PersistenceService, SecurityManager] {
      (updater, persistenceService, securityManager) =>
        log.debug("Required Services present. Creating management collector service")

        val remoteContainerStatePersistor = new RemoteContainerStatePersistor(persistenceService)
        val version = bundleContext.getBundle().getVersion().toString()

        val collectorService = new CollectorServiceImpl(securityManager, updater, remoteContainerStatePersistor, version)
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