package blended.streams.jms

import blended.jms.utils.{JmsAckSession, JmsDestination}
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message._
import blended.util.logging.Logger
import javax.jms._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try
import blended.streams.transaction.FlowHeaderConfig

case class JmsAcknowledgeHandler(
  id : String,
  jmsMessage : Message,
  session : JmsAckSession,
  created : Long = System.currentTimeMillis(),
  log : Logger
) extends AcknowledgeHandler {

  override def deny(): Try[Unit] = Try {
    log.trace(s"Scheduling denial for envelope [$id] in [${session.sessionId}].")
    session.deny(jmsMessage)
  }

  override def acknowledge() : Try[Unit] = Try {
    log.trace(s"Scheduling acknowledgement for envelope [$id] in [${session.sessionId}].")
    session.ack(jmsMessage)
  }
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
  val corrIdHeader : String => String = s => jmsHeaderPrefix(s) + "CorrelationId"
  val priorityHeader : String => String = s => jmsHeaderPrefix(s) + "Priority"
  val expireHeader : String => String = s => jmsHeaderPrefix(s) + "Expiration"
  val deliveryModeHeader : String => String = s => jmsHeaderPrefix(s) + "DeliveryMode"
  val replyToHeader : String => String = s => jmsHeaderPrefix(s) + "ReplyTo"
  val timestampHeader : String => String = s => jmsHeaderPrefix(s) + "Timestamp"

  val replyToQueueName : String = "replyTo"
}

object JmsFlowSupport extends JmsEnvelopeHeader {

  val hyphen : String = "-"
  val dot : String = "."
  val hyphen_repl : String = "_HYPHEN_"
  val dot_repl : String = "_DOT_"

  import MsgProperty.lift

  // Convert a JMS message into a FlowMessage. This is normally used in JMS Sources
  val jms2flowMessage : FlowHeaderConfig => JmsSettings => Message => Try[FlowMessage] = headerConfig => settings => msg => Try {

    val prefix = headerConfig.prefix

    val props: Map[String, MsgProperty] = {

      val dest = JmsDestination.asString(JmsDestination.create(msg.getJMSDestination()).get)
      val delMode = new JmsDeliveryMode(msg.getJMSDeliveryMode()).asString

      val srcVendor : String = Option(msg.getStringProperty(srcVendorHeader(prefix))) match {
        case None => settings.connectionFactory.vendor
        case Some(s) => s
      }

      val srcProvider : String = Option(msg.getStringProperty(srcProviderHeader(prefix))) match {
        case None => settings.connectionFactory.provider
        case Some(s) => s
      }

      val headers : FlowMessageProps = FlowMessage.props(
        srcVendorHeader(prefix) -> srcVendor,
        srcProviderHeader(prefix) -> srcProvider,
        srcDestHeader(prefix) -> dest,
        priorityHeader(prefix) -> msg.getJMSPriority(),
        deliveryModeHeader(prefix) -> delMode,
        timestampHeader(prefix) -> msg.getJMSTimestamp()
      ).get

      val expireHeaderMap : Map[String, MsgProperty] = msg.getJMSExpiration() match {
        case 0L => Map.empty
        case v => Map(expireHeader(prefix) -> MsgProperty.lift(v).get)
      }

      val corrIdMap : Map[String, MsgProperty] =
        Option(msg.getJMSCorrelationID()).map { s => Map(
            corrIdHeader(prefix) -> MsgProperty(s),
            "JMSCorrelationID" -> MsgProperty(s)
          )
        }.getOrElse(Map.empty)

      val props : Map[String, MsgProperty] = msg.getPropertyNames().asScala.map { name =>

        val propName = name.toString()
          .replaceAll(hyphen_repl, hyphen)
          .replaceAll(dot_repl, dot)

        propName -> lift(msg.getObjectProperty(name.toString())).get
      }.toMap

      val replyToMap : Map[String, MsgProperty] =
        Option(msg.getJMSReplyTo()).map( d => replyToHeader(prefix) -> lift(JmsDestination.create(d).get.asString).get).toMap

      props ++ headers ++ expireHeaderMap ++ corrIdMap ++ replyToMap
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
  }


  val envelope2jms : (JmsProducerSettings, Session, FlowEnvelope) => Try[JmsSendParameter] = (settings, session, flowEnv) =>  Try {

    settings.destinationResolver(settings).sendParameter(session, flowEnv).get

  }
}
