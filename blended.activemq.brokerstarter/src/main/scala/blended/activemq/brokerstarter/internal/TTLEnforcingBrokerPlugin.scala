package blended.activemq.brokerstarter.internal

import org.apache.activemq.broker.region.Destination
import org.apache.activemq.broker.region.policy.DeadLetterStrategy
import org.apache.activemq.broker.{BrokerPluginSupport, ProducerBrokerExchange}
import org.apache.activemq.command.{ActiveMQDestination, ActiveMQMessage, Message}

import scala.concurrent.duration.FiniteDuration

/**
 * An Active MQ broker plugin which will enforce the TTL for given destination regardless whether
 * the client has set the TTL. This actually breaks the JMS specification !!!
 */
class TTLEnforcingBrokerPlugin(ttls : Seq[(String, FiniteDuration)]) extends BrokerPluginSupport {

  override def send(
    producerExchange: ProducerBrokerExchange,
    message: Message
  ) : Unit = {

    if (!isDestinationDLQ(message)) {
      overrideTTL(message).foreach{ ttl =>
        val timestamp : Long = System.currentTimeMillis()
        message.setTimestamp(timestamp)
        message.setExpiration(timestamp + ttl)
      }
    }

    super.send(producerExchange, message)
  }

  private def overrideTTL(message : Message) : Option[Long] = ttls.find { case (p, _) =>
    message.getDestination().getQualifiedName().matches(p)
  }.map(_._2).map(_.toMillis).filter(_ >= 0)

  private def isDestinationDLQ(msg : Message) : Boolean = {
    (Option(msg), Option(msg.getRegionDestination().asInstanceOf[Destination])) match {
      case (Some(message), Some(regionDest)) =>
        val dls : DeadLetterStrategy = regionDest.getDeadLetterStrategy()
        val tmp : ActiveMQMessage = new ActiveMQMessage()
        tmp.setDestination(message.getOriginalDestination())
        tmp.setRegionDestination(regionDest)

        val dlqDest : ActiveMQDestination = dls.getDeadLetterQueueFor(tmp, null)

        dlqDest.equals(message.getDestination())
      case _ => false
    }
  }
}
