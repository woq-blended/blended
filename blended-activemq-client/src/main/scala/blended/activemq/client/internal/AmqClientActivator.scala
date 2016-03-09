package blended.activemq.client.internal

import javax.jms.ConnectionFactory

import blended.domino.TypesafeConfigWatching
import domino.DominoActivator
import org.apache.activemq.ActiveMQConnectionFactory
import org.slf4j.LoggerFactory

class AmqClientActivator extends DominoActivator with TypesafeConfigWatching {

  private[this] val log = LoggerFactory.getLogger(classOf[AmqClientActivator])

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
