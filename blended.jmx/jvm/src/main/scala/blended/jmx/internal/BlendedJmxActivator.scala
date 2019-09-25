package blended.jmx.internal

import java.lang.management.ManagementFactory

import blended.akka.ActorSystemWatching
import blended.jmx.statistics.StatisticsActor
import blended.jmx.{BlendedMBeanServerFacade, OpenMBeanExporter, OpenMBeanMapper}
import domino.DominoActivator
import javax.management.MBeanServer

class BlendedJmxActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    val mbeanServer0 : MBeanServer = ManagementFactory.getPlatformMBeanServer()
    mbeanServer0.providesService[MBeanServer]

    val facade : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(mbeanServer0)
    facade.providesService[BlendedMBeanServerFacade]

    val mbeanMapper = new OpenMBeanMapperImpl()
    mbeanMapper.providesService[OpenMBeanMapper]

    val mbeanExporter = new OpenMBeanExporterImpl(mbeanMapper) {
      override protected def mbeanServer: MBeanServer = mbeanServer0
    }
    mbeanExporter.providesService[OpenMBeanExporter]

    whenActorSystemAvailable { osgiConfig =>
      osgiConfig.system.actorOf(StatisticsActor.props(mbeanExporter))
    }

  }

}
