package blended.mgmt.rest.internal

import akka.actor.Props
import blended.akka.ActorSystemWatching
import domino.DominoActivator
import org.osgi.service.http.HttpService
import org.slf4j.LoggerFactory
import blended.updater.remote.RemoteUpdater

class CollectorActivator extends DominoActivator with ActorSystemWatching {

  val log = LoggerFactory.getLogger(classOf[CollectorActivator])

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[RemoteUpdater] { remoteUpdater =>

        // retrieve config as early as possible
        val config = ManagementCollectorConfig(cfg.config, contextPath = "mgmt", remoteUpdater = remoteUpdater)
        log.debug("Config: {}", config)

        whenServicePresent[HttpService] { httpSvc =>
          setupBundleActor(cfg, ManagementCollector.props(cfg = cfg, config))
        }
      }
    }
  }
}

