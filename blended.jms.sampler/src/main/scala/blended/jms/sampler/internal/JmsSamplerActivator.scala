package blended.jms.sampler.internal

import java.lang.management.ManagementFactory
import javax.jms.ConnectionFactory
import javax.management.ObjectName

import blended.container.context.ContainerIdentifierService
import domino.DominoActivator

class JmsSamplerActivator extends DominoActivator {

  whenBundleActive {
    whenServicePresent[ContainerIdentifierService] { idSvc =>
      whenAdvancedServicePresent[ConnectionFactory]("provider=activemq") {
        cf =>
          val sampler = new JmsSampler(idSvc, cf)
          ManagementFactory.getPlatformMBeanServer().registerMBean(sampler, new ObjectName("blended:type=JmsSampler"))
      }
    }
  }
}
