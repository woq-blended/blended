package blended.mgmt.base.internal

import blended.mgmt.base.FrameworkService
import domino.DominoActivator

class MgmtActivator extends DominoActivator {

  whenBundleActive {
    (new FrameworkServiceImpl(bundleContext)).providesService[FrameworkService]
  }
}
