package blended.mgmt.service.jmx.internal

import javax.management.MBeanServer

import blended.akka.ActorSystemWatching
import domino.DominoActivator

class ServiceJmxActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>

      whenServicePresent[MBeanServer] { server =>
        val config = ServiceJmxConfig(cfg.config)
        setupBundleActor(cfg, ServiceJmxCollector.props(cfg, ServiceJmxConfig(cfg.config), server))
      }
    }
  }
}
