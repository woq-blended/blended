package blended.activemq.brokerstarter

import blended.activemq.brokerstarter.internal.{BrokerConfig, BrokerControlSupervisor}
import blended.akka.ActorSystemWatching
import blended.util.config.Implicits._
import blended.util.logging.Logger
import domino.DominoActivator
import javax.net.ssl.SSLContext

class BrokerActivator
  extends DominoActivator
  with ActorSystemWatching  {

  private[this] val log = Logger[BrokerActivator]

  whenBundleActive {
    whenActorSystemAvailable { osgiCfg =>

      try {

        val brokerConfigs = osgiCfg.config.getConfigMap("broker", Map.empty).map { case (brokerName, cfg) =>
          (brokerName -> BrokerConfig.create(brokerName, osgiCfg.idSvc, cfg).get)
        }

        val withSsl = brokerConfigs.values.exists(_.withSsl)

        if (withSsl) {
          log.info("Starting configured ActiveMQ brokers with SSL")
          whenAdvancedServicePresent[SSLContext]("(type=server)") { sslCtxt =>

            val supervisor = osgiCfg.system.actorOf(BrokerControlSupervisor.props(
              osgiCfg, Some(sslCtxt), brokerConfigs.values.toList
            ), bundleContext.getBundle().getSymbolicName())

            onStop {
              log.info("Stopping configured ActiveMQ brokers ")
              osgiCfg.system.stop(supervisor)
            }
          }

        } else {
          log.info("Starting configured ActiveMQ brokers without SSL")
          val supervisor = osgiCfg.system.actorOf(BrokerControlSupervisor.props(
            osgiCfg, None, brokerConfigs.values.toList
          ), bundleContext.getBundle().getSymbolicName())

          onStop {
            log.info("Stopping configured ActiveMQ brokers ")
            osgiCfg.system.stop(supervisor)
          }
        }
      }
    }
  }
}
