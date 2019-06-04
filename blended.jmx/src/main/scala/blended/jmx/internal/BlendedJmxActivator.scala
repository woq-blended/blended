package blended.jmx.internal

import java.lang.management.ManagementFactory

import domino.DominoActivator
import javax.management.MBeanServer

class BlendedJmxActivator extends DominoActivator {

  whenBundleActive {
    ManagementFactory.getPlatformMBeanServer.providesService[MBeanServer]
  }

}
