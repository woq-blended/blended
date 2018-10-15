package blended.streams.jms

import akka.util.ByteString
import blended.jms.utils.JmsDestination
import blended.streams.message._
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

trait JmsEnvelopeHeader {
  val jmsHeaderPrefix : String => String = s => s + "JMS"

  val srcVendorHeader : String => String = s => jmsHeaderPrefix(s) + "SrcVendor"
  val srcProviderHeader : String => String = s => jmsHeaderPrefix(s) + "SrcProvider"
  val srcDestHeader : String => String = s => jmsHeaderPrefix(s) + "SrcDestination"
  val destHeader : String => String = s => jmsHeaderPrefix(s) + "Destination"
  val corrIdHeader : String => String = s => jmsHeaderPrefix(s) + "CorrelationId"
  val priorityHeader : String => String = s => jmsHeaderPrefix(s) + "Priority"
  val expireHeader : String => String = s => jmsHeaderPrefix(s) + "Expiration"
  val deliveryModeHeader : String => String = s => jmsHeaderPrefix(s) + "DeliveryMode"
  val replyToHeader : String => String = s => jmsHeaderPrefix(s) + "ReplyTo"
}

object JmsFlowSupport extends JmsEnvelopeHeader {

  import MsgProperty.lift

  // Convert a JMS message into a FlowMessage. This is normally used in JMS Sources
  val jms2flowMessage : (JmsSettings, Message) => Try[FlowMessage] = { (settings, msg) => Try {

    val prefix = settings.headerPrefix

    val props: Map[String, MsgProperty[_]] = {

      val dest = JmsDestination.asString(JmsDestination.create(msg.getJMSDestination()).get)

      val delMode = new JmsDeliveryMode(msg.getJMSDeliveryMode()).asString

      val headers : Map[String, MsgProperty[_]] = Map(
        srcVendorHeader(prefix) -> MsgProperty.lift(settings.connectionFactory.vendor).get,
        srcProviderHeader(prefix) -> MsgProperty.lift(settings.connectionFactory.provider).get,
        srcDestHeader(prefix) -> MsgProperty.lift(dest).get,
        priorityHeader(prefix) -> MsgProperty.lift(msg.getJMSPriority()).get,
        deliveryModeHeader(prefix) -> MsgProperty.lift(msg.getJMSDeliveryMode()).get,
        expireHeader(prefix) -> MsgProperty.lift(msg.getJMSExpiration()).get,
        deliveryModeHeader(prefix) -> MsgProperty.lift(delMode).get
      )

      val corrIdMap : Map[String, MsgProperty[_]] =
        Option(msg.getJMSCorrelationID()).map( s => corrIdHeader(prefix) -> MsgProperty.lift(s).get).toMap

      val props : Map[String, MsgProperty[_]] = msg.getPropertyNames().asScala.map { name =>
        (name.toString -> lift(msg.getObjectProperty(name.toString())).get)
      }.toMap

      val replyToMap : Map[String, MsgProperty[_]] =
        Option(msg.getJMSReplyTo()).map( d => replyToHeader(prefix) -> lift(JmsDestination.create(d).get.asString).get).toMap

      props ++ headers ++ corrIdMap ++ replyToMap
    }

    val flowMessge = msg match {
      case t : TextMessage =>
        TextFlowMessage(t.getText(), props)

      case b : BytesMessage => {
        val content : Array[Byte] = new Array[Byte](b.getBodyLength().toInt)
        b.readBytes(content)
        BinaryFlowMessage(content, props)
      }

      case _ => FlowMessage(props)
    }

    flowMessge
  }}


  val envelope2jms : (JmsProducerSettings, Session, FlowEnvelope) => Try[JmsSendParameter] = (settings, session, flowEnv) =>  Try {

    settings.destinationResolver(settings).sendParameter(session, flowEnv)

  }
}
