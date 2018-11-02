package blended.streams.jms

import blended.jms.utils.JmsDestination
import blended.streams.message.{BinaryFlowMessage, FlowEnvelope, TextFlowMessage}
import javax.jms.{JMSException, Message, Session}
import scala.concurrent.duration._

trait JmsDestinationResolver {

  def sendParameter(session: Session, env: FlowEnvelope) : JmsSendParameter

  def createJmsMessage(session : Session, env : FlowEnvelope) : Message = {

    val flowMsg = env.flowMessage

    val msg = flowMsg match {
      case t :
        TextFlowMessage => session.createTextMessage(t.getText())
      case b :
        BinaryFlowMessage =>
        val r = session.createBytesMessage()
        r.writeBytes(b.getBytes().toArray)

        r
      case _ => session.createMessage()
    }

    flowMsg.header.filter {
      case (k, v) => !k.startsWith("JMS")
    }.foreach {
      case (k,v) => msg.setObjectProperty(k, v.value)
    }

    msg
  }
}

class SettingsDestinationResolver(settings: JmsProducerSettings)
  extends JmsDestinationResolver
  with JmsEnvelopeHeader {

  private val prefix = settings.headerConfig.prefix

  override def sendParameter(session: Session, env: FlowEnvelope): JmsSendParameter = {

    val msg = createJmsMessage(session, env)
    // Get the destination
    val dest : JmsDestination = settings.jmsDestination match {
      case Some(d) => d
      case None => throw new JMSException(s"Could not resolve JMS destination for [$env]")
    }

    // Always try to get the CorrelationId from the flow Message
    env.flowMessage.header[String](corrIdHeader(prefix)) match {
      case Some(id) => msg.setJMSCorrelationID(id)
      case None => settings.correlationId().foreach(msg.setJMSCorrelationID)
    }

    JmsSendParameter(
      message = msg,
      destination = dest,
      deliveryMode = settings.deliveryMode,
      priority = settings.priority,
      ttl = settings.timeToLive
    )
  }
}

class MessageDestinationResolver(settings: JmsProducerSettings)
  extends JmsDestinationResolver
  with JmsEnvelopeHeader {

  private val prefix = settings.headerConfig.prefix

  override def sendParameter(session: Session, env: FlowEnvelope): JmsSendParameter = {

    val flowMsg = env.flowMessage
    val msg = createJmsMessage(session, env)

    // Get the destination
    val dest : JmsDestination = flowMsg.header[String](s"${destHeader(prefix)}") match {
      case Some(s) => JmsDestination.create(s).get
      case None => settings.jmsDestination match {
        case Some(d) => d
        case None =>
          throw new JMSException(s"Could not resolve JMS destination for [$flowMsg]")
      }
    }

    // Always try to get the CorrelationId from the flow Message
    flowMsg.header[String](corrIdHeader(prefix)) match {
      case Some(id) => msg.setJMSCorrelationID(id)
      case None => settings.correlationId().foreach(msg.setJMSCorrelationID)
    }

    val prio = flowMsg.header[Int](priorityHeader(prefix)) match {
      case Some(p) => p
      case None => settings.priority
    }

    val timeToLive : Option[FiniteDuration] = flowMsg.header[Long](expireHeader(prefix)) match {
      case Some(l) => Some( (Math.max(1L, l - System.currentTimeMillis())).millis)
      case None => settings.timeToLive
    }

    val delMode : JmsDeliveryMode = flowMsg.header[String](deliveryModeHeader(prefix)) match {
      case Some(s) => JmsDeliveryMode.create(s).get
      case None => settings.deliveryMode
    }

    JmsSendParameter(
      message = msg,
      destination = dest,
      deliveryMode = delMode,
      priority = prio,
      ttl = timeToLive
    )

  }

}
