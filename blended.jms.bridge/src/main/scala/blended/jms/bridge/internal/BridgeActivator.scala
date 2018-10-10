package blended.jms.bridge.internal

import blended.container.context.api.ContainerIdentifierService
import domino.DominoActivator

class BridgeActivator extends DominoActivator {

  whenBundleActive {
    whenServicePresent[ContainerIdentifierService] { idSvc =>

    }
  }

}
