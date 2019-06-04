package blended.mgmt.service.jmx.internal

import blended.akka.ActorSystemWatching
import domino.DominoActivator
import javax.management.MBeanServer

class ServiceJmxActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[MBeanServer] { server =>
        val config = ServiceJmxConfig(cfg.config)
        setupBundleActor(cfg, ServiceJmxCollector.props(cfg, config, server))
      }
    }
  }
}
