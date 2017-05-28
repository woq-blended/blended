package blended.jmx.internal

import java.lang.management.ManagementFactory
import javax.management.MBeanServer

import domino.DominoActivator

class BlendedJmxActivator extends DominoActivator {

  whenBundleActive {
    ManagementFactory.getPlatformMBeanServer.providesService[MBeanServer]
  }

}
