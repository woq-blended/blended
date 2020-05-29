package blended.streams.jms

import blended.jms.utils.JmsDestination
import blended.streams.FlowHeaderConfig
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message._
import blended.util.RichTry._
import javax.jms._

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.util.Try

object JmsSendParameter {
  val defaultPriority : Int = 4
}

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
  val corrIdHeader : String => String = s => jmsHeaderPrefix(s) + "CorrelationID"
  val priorityHeader : String => String = s => jmsHeaderPrefix(s) + "Priority"
  val expireHeader : String => String = s => jmsHeaderPrefix(s) + "Expiration"
  val deliveryModeHeader : String => String = s => jmsHeaderPrefix(s) + "DeliveryMode"
  val replyToHeader : String => String = s => jmsHeaderPrefix(s) + "ReplyTo"
  val timestampHeader : String => String = s => jmsHeaderPrefix(s) + "Timestamp"
  val typeHeader : String => String = s => jmsHeaderPrefix(s) + "Type"
  val msgIdHeader : String => String = s => jmsHeaderPrefix(s) + "MessageId"

  val replyToQueueName : String = "replyTo"
}

object JmsFlowSupport extends JmsEnvelopeHeader {

  // Convert a JMS message into a FlowMessage. This is normally used in JMS Sources
  val jms2flowMessage : FlowHeaderConfig => JmsSettings => Message => Try[FlowMessage] = headerConfig => settings => msg => Try {

    val prefix = headerConfig.prefix

    val props : Map[String, MsgProperty] = {

      val dest = JmsDestination.asString(JmsDestination.create(msg.getJMSDestination()).get)
      val delMode = new JmsDeliveryMode(msg.getJMSDeliveryMode()).asString

      val srcVendor : String = Option(msg.getStringProperty(srcVendorHeader(prefix))) match {
        case None    => settings.connectionFactory.vendor
        case Some(s) => s
      }

      val srcProvider : String = Option(msg.getStringProperty(srcProviderHeader(prefix))) match {
        case None    => settings.connectionFactory.provider
        case Some(s) => s
      }

      val headers : FlowMessageProps = FlowMessage.props(
        srcVendorHeader(prefix) -> srcVendor,
        srcProviderHeader(prefix) -> srcProvider,
        srcDestHeader(prefix) -> dest,
        priorityHeader(prefix) -> msg.getJMSPriority(),
        deliveryModeHeader(prefix) -> delMode,
        timestampHeader(prefix) -> msg.getJMSTimestamp(),
        typeHeader(prefix) -> msg.getJMSType(),
        msgIdHeader(prefix) -> msg.getJMSMessageID()
      ).unwrap

      val expireHeaderMap : Map[String, MsgProperty] = msg.getJMSExpiration() match {
        case 0L => Map.empty
        case v  => Map(expireHeader(prefix) -> MsgProperty(v).unwrap)
      }

      val corrIdMap : Map[String, MsgProperty] =
        Option(msg.getJMSCorrelationID()).map { s =>
          Map(
            corrIdHeader(prefix) -> MsgProperty(s).unwrap,
            "JMSCorrelationID" -> MsgProperty(s).unwrap
          )
        }.getOrElse(Map.empty)

      val props : Map[String, MsgProperty] = msg.getPropertyNames().asScala.map { name =>

        val propName = settings.keyFormatStrategy.handleReceivedKey(name.toString())

        propName -> MsgProperty(msg.getObjectProperty(name.toString())).unwrap
      }.toMap

      val replyToMap : Map[String, MsgProperty] =
        Option(msg.getJMSReplyTo()).map(d => replyToHeader(prefix) -> MsgProperty(JmsDestination.create(d).unwrap.asString).unwrap).toMap

      props ++ headers ++ expireHeaderMap ++ corrIdMap ++ replyToMap
    }

    val flowMessage = msg match {
      case t : TextMessage =>
        TextFlowMessage(t.getText(), props)

      case b : BytesMessage =>
        val content : Array[Byte] = new Array[Byte](b.getBodyLength().toInt)
        b.readBytes(content)
        BinaryFlowMessage(content, props)

      case _ => FlowMessage(props)
    }

    flowMessage
  }

  val envelope2jms : (JmsProducerSettings, Session, FlowEnvelope) => Try[JmsSendParameter] = (settings, session, flowEnv) => Try {

    settings.destinationResolver(settings).sendParameter(session, flowEnv).unwrap

  }
}
