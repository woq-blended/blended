package blended.jmx.internal

import java.lang.management.ManagementFactory

import blended.jmx.BlendedMBeanServerFacade
import domino.DominoActivator
import javax.management.MBeanServer

class BlendedJmxActivator extends DominoActivator {

  whenBundleActive {
    val mbeanServer : MBeanServer = ManagementFactory.getPlatformMBeanServer()
    mbeanServer.providesService[MBeanServer]

    val facade : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(mbeanServer)
    facade.providesService[BlendedMBeanServerFacade]
  }

}
