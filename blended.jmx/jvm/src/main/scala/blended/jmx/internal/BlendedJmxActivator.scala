package blended.jmx.internal

import java.lang.management.ManagementFactory

import blended.jmx.{BlendedMBeanServerFacade, OpenMBeanExporter, OpenMBeanMapper}
import blended.jmx.impl.OpenMBeanMapperImpl
import domino.DominoActivator
import javax.management.MBeanServer

class BlendedJmxActivator extends DominoActivator {

  whenBundleActive {
    val mbeanServer : MBeanServer = ManagementFactory.getPlatformMBeanServer()
    mbeanServer.providesService[MBeanServer]

    val facade : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(mbeanServer)
    facade.providesService[BlendedMBeanServerFacade]

    val mbeanMapper = new OpenMBeanMapperImpl()
    mbeanMapper.providesService[OpenMBeanMapper]

    val mbeanExporter = new OpenMBeanExporterImpl(mbeanMapper) {
      override protected def mbeanServer: MBeanServer = mbeanServer
    }
    mbeanExporter.providesService[OpenMBeanExporter]
  }

}
