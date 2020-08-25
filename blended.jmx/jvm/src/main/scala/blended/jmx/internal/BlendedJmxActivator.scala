package blended.jmx.internal

import java.lang.management.ManagementFactory

import blended.akka.ActorSystemWatching
import blended.jmx.{BlendedMBeanServerFacade, NamingStrategy, NamingStrategyResolver}
import domino.DominoActivator
import javax.management.MBeanServer
import blended.jmx.ProductMBeanManager
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext
import blended.jmx.statistics.ServiceStatisticsActor

class BlendedJmxActivator extends DominoActivator with ActorSystemWatching {

  private class OsgiStrategyResolver(
    override val bundleContext : BundleContext
  ) extends NamingStrategyResolver 
    with ServiceConsuming {

    override def resolveNamingStrategy(v: Product): Option[NamingStrategy] = {
      withAdvancedService[NamingStrategy, Option[NamingStrategy]](s"(${NamingStrategyResolver.strategyClassNameProp}=${v.getClass().getName()})") { s => s }
    }
  }

  whenBundleActive {
    val mbeanServer : MBeanServer = ManagementFactory.getPlatformMBeanServer()
    mbeanServer.providesService[MBeanServer]

    val facade : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(mbeanServer)
    facade.providesService[BlendedMBeanServerFacade]

    whenActorSystemAvailable { osgiConfig =>
      val mgr : ProductMBeanManager = new  ProductMBeanManagerImpl(
        osgiConfig.system,
        new OsgiStrategyResolver(bundleContext),
        mbeanServer, 
        new OpenMBeanMapperImpl()
      ) 

      osgiConfig.system.actorOf(ServiceStatisticsActor.props(mgr))

      mgr.start()
      mgr.providesService[ProductMBeanManager]

      onStop {
        mgr.stop()
      }
    }
  }
}
