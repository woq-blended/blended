package blended.mgmt.agent.internal

import blended.akka.ActorSystemWatching
import domino.DominoActivator

// The Activator that is called from the OSGi framework whenever the bundle is started or stopped.
class AgentActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      setupBundleActor(cfg, OsgiMgmtReporter.props(cfg))
    }
  }
}
