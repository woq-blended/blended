package blended.jmx.internal

import java.lang.management.ManagementFactory

import blended.akka.ActorSystemWatching
import blended.akka.internal.ActorSystemCapsule
import blended.jmx.statistics.StatisticsActor
import blended.jmx.{BlendedMBeanServerFacade, OpenMBeanExporter, OpenMBeanMapper}
import domino.DominoActivator
import javax.management.MBeanServer

class BlendedJmxActivator extends DominoActivator with ActorSystemWatching {

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

    whenActorSystemAvailable { osgiConfig =>
      osgiConfig.system.actorOf(StatisticsActor.props(mbeanExporter))
    }

  }

}
