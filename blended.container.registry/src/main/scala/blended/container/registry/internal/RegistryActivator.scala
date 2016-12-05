package blended.container.registry.internal

import blended.akka.ActorSystemWatching
import domino.DominoActivator

class RegistryActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      setupBundleActor(cfg, ContainerRegistryImpl.props(cfg))
    }
  }
}
