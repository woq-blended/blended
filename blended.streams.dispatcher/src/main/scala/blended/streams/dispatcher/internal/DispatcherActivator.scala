package blended.streams.dispatcher.internal

import blended.akka.ActorSystemWatching
import blended.jms.bridge.BridgeProviderRegistry
import domino.DominoActivator

class DispatcherActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[BridgeProviderRegistry] { registry =>



      }
    }
  }

}
