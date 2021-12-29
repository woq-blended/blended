package blended.jmx.internal

import blended.akka.ActorSystemWatching
import blended.jmx.statistics.{ServiceNamingStrategy, ServicePublishEntry}
import blended.jmx.{BlendedMBeanServerFacade, NamingStrategy, NamingStrategyResolver, ProductMBeanManager}
import domino.DominoActivator
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

import java.lang.management.ManagementFactory
import javax.management.MBeanServer

class BlendedJmxActivator extends DominoActivator with ActorSystemWatching {

  private class OsgiStrategyResolver(
    override val bundleContext: BundleContext
  ) extends NamingStrategyResolver
      with ServiceConsuming {

    override def resolveNamingStrategy(v: Product): Option[NamingStrategy] = {
      withAdvancedService[NamingStrategy, Option[NamingStrategy]](
        s"(${NamingStrategyResolver.strategyClassNameProp}=${v.getClass().getName()})"
      ) { s => s }
    }
  }

  whenBundleActive {
    val mbeanServer: MBeanServer = ManagementFactory.getPlatformMBeanServer()
    mbeanServer.providesService[MBeanServer]

    val facade: BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(mbeanServer)
    facade.providesService[BlendedMBeanServerFacade]

    val svcNaming: NamingStrategy = new ServiceNamingStrategy()
    svcNaming.providesService[NamingStrategy](
      NamingStrategyResolver.strategyClassNameProp -> classOf[ServicePublishEntry].getName()
    )

    whenActorSystemAvailable { osgiConfig =>
      val mgr: ProductMBeanManager = new ProductMBeanManagerImpl(
        osgiConfig.system,
        new OsgiStrategyResolver(bundleContext),
        mbeanServer,
        new OpenMBeanMapperImpl()
      )

      mgr.start()
      mgr.providesService[ProductMBeanManager]

      onStop {
        mgr.stop()
      }
    }
  }
}
