package blended.activemq.brokerstarter.internal

import blended.util.logging.Logger
import org.apache.activemq.broker.{Broker, BrokerPlugin}

/**
 * An ActiveMQ plugin that allows authentication against an arbitrary blended login module.
 */
class JaasAuthenticationPlugin extends BrokerPlugin {

  private val log : Logger = Logger[JaasAuthenticationPlugin]

  override def installPlugin(broker: Broker): Broker = {
    log.info(s"Activating blended JAAS authentication for Active MQ broker [${broker.getBrokerName()}]")
    new JaasAuthenticatingBroker(broker)
  }
}
