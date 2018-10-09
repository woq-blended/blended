package blended.streams.jms

import akka.util.ByteString
import blended.jms.utils.JmsDestination
import blended.streams.message.{BinaryFlowMessage, FlowMessage, MsgProperty, TextFlowMessage}
import javax.jms._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try

final case class JmsSendParameter(
  message : Message,
  destination : JmsDestination,
  deliveryMode : JmsDeliveryMode = JmsDeliveryMode.Persistent,
  priority : Int,
  ttl : Option[FiniteDuration]
)

object JmsFlowMessage {

  private[this] val jmsHeaderPrefix : JmsSettings => String = settings => settings.headerPrefix + "JMS"

  private[this] val destHeader = "Destination"
  private[this] val corrIdHeader = "CorrelationId"
  private[this] val priorityHeader = "Priority"
  private[this] val expireHeader = "Expiration"
  private[this] val deliveryModeHeader = "DeliveryMode"

  // Convert a JMS message into a FlowMessage. This is normally used in JMS Sources
  val jms2flowMessage : (JmsSettings, Message) => FlowMessage = { (settings, msg) =>

    val props : Map[String, MsgProperty[_]] = msg.getPropertyNames().asScala.map { name =>
      (name.toString, MsgProperty.lift(msg.getObjectProperty(name.toString())).get)
    }.toMap

    msg match {
      case t : TextMessage =>
        TextFlowMessage(props, t.getText())

      case b : BytesMessage => {
        val content : Array[Byte] = new Array[Byte](b.getBodyLength().toInt)
        b.readBytes(content)
        BinaryFlowMessage(props, ByteString(content))
      }

      case _ => FlowMessage(props)
    }
  }

  val flowMessage2jms : (JmsProducerSettings, Session, FlowMessage) => Try[JmsSendParameter] = (settings, session, flowMsg) =>  Try {
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

    flowMsg.header.filter{
      case (k, v) => !k.startsWith("JMS")
    }.foreach {
      case (k,v) => msg.setObjectProperty(k, v.value)
    }

    // Get the destination
    val dest : JmsDestination = if (settings.sendParamsFromMessage) {
      flowMsg.header[String](s"${jmsHeaderPrefix(settings)}$destHeader") match {
        case Some(s) => JmsDestination.create(s).get
        case None => settings.jmsDestination match {
          case Some(d) => d
          case None => throw new JMSException(s"Could not resolve JMS destination for [$flowMsg]")
        }
      }
    } else {
      settings.jmsDestination match {
        case Some(d) => d
        case None => throw new JMSException(s"Could not resolve JMS destination for [$flowMsg]")
      }
    }

    // Always try to get the CorrelationId from the flow Message
    flowMsg.header[String](s"${jmsHeaderPrefix(settings)}$corrIdHeader") match {
      case Some(id) => msg.setJMSCorrelationID(id)
      case None => settings.correlationId().foreach(msg.setJMSCorrelationID)
    }

    val prio = if(settings.sendParamsFromMessage) {
      flowMsg.header[Int](s"${jmsHeaderPrefix}$priorityHeader") match {
        case Some(p) => p
        case None => settings.priority
      }
    } else {
      settings.priority
    }

    val timeToLive : Option[FiniteDuration] = if (settings.sendParamsFromMessage) {
      flowMsg.header[Long](s"${jmsHeaderPrefix}$expireHeader") match {
        case Some(l) => Some( (l - System.currentTimeMillis()).millis )
        case None => settings.timeToLive
      }
    } else {
      settings.timeToLive
    }

    val delMode : JmsDeliveryMode = if (settings.sendParamsFromMessage) {
      flowMsg.header[String](s"${jmsHeaderPrefix}$deliveryModeHeader") match {
        case Some(s) => JmsDeliveryMode.create(s).get
        case None => settings.deliveryMode
      }
    } else {
      settings.deliveryMode
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
