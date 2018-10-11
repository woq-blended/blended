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

  import MsgProperty.lift

  private[this] val jmsHeaderPrefix : JmsSettings => String = settings => settings.headerPrefix + "JMS"

  private[this] val destHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "Destination"
  private[this] val corrIdHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "CorrelationId"
  private[this] val priorityHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "Priority"
  private[this] val expireHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "Expiration"
  private[this] val deliveryModeHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "DeliveryMode"

  // Convert a JMS message into a FlowMessage. This is normally used in JMS Sources
  val jms2flowMessage : (JmsSettings, Message) => Try[FlowMessage] = { (settings, msg) => Try {

    expireHeader

    val props: Map[String, MsgProperty[_]] = {

      val dest = JmsDestination.asString(JmsDestination.create(msg.getJMSDestination()).get)

      val headers : Map[String, MsgProperty[_]] = Map(
        destHeader(settings) -> MsgProperty.lift(dest).get,
        priorityHeader(settings) -> MsgProperty.lift(msg.getJMSPriority()).get,
        deliveryModeHeader(settings) -> MsgProperty.lift(msg.getJMSDeliveryMode()).get,
        expireHeader(settings) -> MsgProperty.lift(msg.getJMSExpiration()).get
      )

      val corrIdMap : Map[String, MsgProperty[_]] =
        Option(msg.getJMSCorrelationID()).map( s => corrIdHeader(settings) -> MsgProperty.lift(s).get).toMap

      val props : Map[String, MsgProperty[_]] = msg.getPropertyNames().asScala.map { name =>
        (name.toString -> lift(msg.getObjectProperty(name.toString())).get)
      }.toMap

      props ++ headers ++ corrIdMap
    }

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
  }}


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

    flowMsg.header.filter {
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
    flowMsg.header[String](corrIdHeader(settings)) match {
      case Some(id) => msg.setJMSCorrelationID(id)
      case None => settings.correlationId().foreach(msg.setJMSCorrelationID)
    }

    val prio = if(settings.sendParamsFromMessage) {
      flowMsg.header[Int](priorityHeader(settings)) match {
        case Some(p) => p
        case None => settings.priority
      }
    } else {
      settings.priority
    }

    val timeToLive : Option[FiniteDuration] = if (settings.sendParamsFromMessage) {
      flowMsg.header[Long](expireHeader(settings)) match {
        case Some(l) => Some( (Math.max(1L, l - System.currentTimeMillis())).millis)
        case None => settings.timeToLive
      }
    } else {
      settings.timeToLive
    }

    val delMode : JmsDeliveryMode = if (settings.sendParamsFromMessage) {
      flowMsg.header[String](deliveryModeHeader(settings)) match {
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
