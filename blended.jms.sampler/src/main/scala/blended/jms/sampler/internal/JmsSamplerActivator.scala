package blended.jms.sampler.internal

import java.lang.management.ManagementFactory

import blended.akka.ActorSystemWatching
import domino.DominoActivator
import javax.jms.ConnectionFactory
import javax.management.ObjectName

class JmsSamplerActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenAdvancedServicePresent[ConnectionFactory]("(provider=activemq)") {
        cf =>
          val sampler = JmsSampler(cfg, cf)
          ManagementFactory.getPlatformMBeanServer().registerMBean(sampler, new ObjectName("blended:type=JmsSampler"))
      }
    }
  }
}
