package blended.streams.jms

import blended.jms.utils.JmsDestination
import blended.streams.message.{BinaryFlowMessage, FlowEnvelope, FlowMessage, TextFlowMessage}
import javax.jms.{JMSException, Message, Session}

import scala.concurrent.duration._
import scala.util.Try

trait JmsDestinationResolver { this : JmsEnvelopeHeader =>

  def settings : JmsProducerSettings

  def sendParameter(session: Session, env: FlowEnvelope) : Try[JmsSendParameter]

  // Get the destination from the message
  val destination : FlowMessage => Try[JmsDestination] = { flowMsg => Try {
    flowMsg.header[String](s"${destHeader(settings.headerConfig.prefix)}") match {
      case Some(s) => JmsDestination.create(s).get
      case None => settings.jmsDestination match {
        case Some(d) => d
        case None =>
          throw new JMSException(s"Could not resolve JMS destination for [$flowMsg]")
      }
    }
  }}

  val priority : FlowMessage => Int  = { flowMsg =>
    flowMsg.header[Int](priorityHeader(settings.headerConfig.prefix)) match {
      case Some(p) => p
      case None => settings.priority
    }
  }

  val timeToLive : FlowMessage => Option[FiniteDuration] = { flowMsg =>
    flowMsg.header[Long](expireHeader(settings.headerConfig.prefix)) match {
      case Some(l) => Some( (Math.max(1L, l - System.currentTimeMillis())).millis)
      case None => settings.timeToLive
    }
  }

  val deliveryMode : FlowMessage => JmsDeliveryMode = { flowMsg =>
    flowMsg.header[String](deliveryModeHeader(settings.headerConfig.prefix)) match {
      case Some(s) => JmsDeliveryMode.create(s).get
      case None => settings.deliveryMode
    }
  }


  def createJmsMessage(session : Session, env : FlowEnvelope) : Message = {
    val prefix = settings.headerConfig.prefix

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

    // Always try to get the CorrelationId from the flow Message
    env.flowMessage.header[String](corrIdHeader(prefix)) match {
      case Some(id) => msg.setJMSCorrelationID(id)
      case None => settings.correlationId().foreach(msg.setJMSCorrelationID)
    }

    msg
  }
}

class SettingsDestinationResolver(override val settings: JmsProducerSettings)
  extends JmsDestinationResolver
  with JmsEnvelopeHeader {

  override def sendParameter(session: Session, env: FlowEnvelope): Try[JmsSendParameter] = Try {

    val msg = createJmsMessage(session, env)
    // Get the destination
    val dest : JmsDestination = settings.jmsDestination match {
      case Some(d) => d
      case None => throw new JMSException(s"Could not resolve JMS destination for [$env]")
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

class MessageDestinationResolver(override val settings: JmsProducerSettings)
  extends JmsDestinationResolver
  with JmsEnvelopeHeader {

  private val prefix = settings.headerConfig.prefix

  override def sendParameter(session: Session, env: FlowEnvelope): Try[JmsSendParameter] = Try {

    val flowMsg = env.flowMessage
    val msg = createJmsMessage(session, env)

    // Get the destination
    val dest : JmsDestination = destination(flowMsg).get

    // Get the priority
    val prio : Int = priority(flowMsg)

    // Get the TTL
    val ttl : Option[FiniteDuration] = timeToLive(flowMsg)

    // Get the delivery mode
    val delMode : JmsDeliveryMode = deliveryMode(flowMsg)

    JmsSendParameter(
      message = msg,
      destination = dest,
      deliveryMode = delMode,
      priority = prio,
      ttl = ttl
    )
  }
}
