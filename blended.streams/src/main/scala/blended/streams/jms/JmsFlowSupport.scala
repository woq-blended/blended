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
  val jmsHeaderPrefix : JmsSettings => String = settings => settings.headerPrefix + "JMS"

  val srcVendorHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "SrcVendor"
  val srcProviderHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "SrcProvider"
  val srcDestHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "SrcDestination"
  val destHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "Destination"
  val corrIdHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "CorrelationId"
  val priorityHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "Priority"
  val expireHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "Expiration"
  val deliveryModeHeader : JmsSettings => String = s => jmsHeaderPrefix(s) + "DeliveryMode"
}

object JmsFlowSupport extends JmsEnvelopeHeader {

  import MsgProperty.lift

  // Convert a JMS message into a FlowMessage. This is normally used in JMS Sources
  val jms2flowMessage : (JmsSettings, Message) => Try[FlowMessage] = { (settings, msg) => Try {

    expireHeader

    val props: Map[String, MsgProperty[_]] = {

      val dest = JmsDestination.asString(JmsDestination.create(msg.getJMSDestination()).get)

      val delMode = new JmsDeliveryMode(msg.getJMSDeliveryMode()).asString

      val headers : Map[String, MsgProperty[_]] = Map(
        srcVendorHeader(settings) -> MsgProperty.lift(settings.connectionFactory.vendor).get,
        srcProviderHeader(settings) -> MsgProperty.lift(settings.connectionFactory.provider).get,
        srcDestHeader(settings) -> MsgProperty.lift(dest).get,
        priorityHeader(settings) -> MsgProperty.lift(msg.getJMSPriority()).get,
        deliveryModeHeader(settings) -> MsgProperty.lift(msg.getJMSDeliveryMode()).get,
        expireHeader(settings) -> MsgProperty.lift(msg.getJMSExpiration()).get,
        deliveryModeHeader(settings) -> MsgProperty.lift(delMode).get
      )

      val corrIdMap : Map[String, MsgProperty[_]] =
        Option(msg.getJMSCorrelationID()).map( s => corrIdHeader(settings) -> MsgProperty.lift(s).get).toMap

      val props : Map[String, MsgProperty[_]] = msg.getPropertyNames().asScala.map { name =>
        (name.toString -> lift(msg.getObjectProperty(name.toString())).get)
      }.toMap

      props ++ headers ++ corrIdMap
    }

    val flowMessge = msg match {
      case t : TextMessage =>
        TextFlowMessage(t.getText(), props)

      case b : BytesMessage => {
        val content : Array[Byte] = new Array[Byte](b.getBodyLength().toInt)
        b.readBytes(content)
        BinaryFlowMessage(ByteString(content), props)
      }

      case _ => FlowMessage(props)
    }

    flowMessge
  }}


  val envelope2jms : (JmsProducerSettings, Session, FlowEnvelope) => Try[JmsSendParameter] = (settings, session, flowEnv) =>  Try {

    settings.destinationResolver(settings).sendParameter(session, flowEnv)

  }
}
