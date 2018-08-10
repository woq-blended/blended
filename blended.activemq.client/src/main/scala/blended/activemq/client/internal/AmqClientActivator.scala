package blended.activemq.client.internal

import blended.domino.TypesafeConfigWatching
import blended.util.logging.Logger
import domino.DominoActivator
import domino.logging.Logging
import javax.jms.ConnectionFactory
import org.apache.activemq.ActiveMQConnectionFactory

class AmqClientActivator extends DominoActivator with TypesafeConfigWatching with Logging {

  private[this] val log = Logger[AmqClientActivator]

  whenBundleActive {
    whenTypesafeConfigAvailable { (cfg, idSvc) =>

      if (!cfg.isEmpty) {
        val url = cfg.getString("brokerUrl")
        log.info(s"Creating connection factory to broker [$url]")
        val cf = new ActiveMQConnectionFactory(url)
        cf.providesService[ConnectionFactory](Map("provider" -> "activemq"))
      } else {
        log.info("No ActiveMQ client configuration, no client started")
      }
    }
  }
}
