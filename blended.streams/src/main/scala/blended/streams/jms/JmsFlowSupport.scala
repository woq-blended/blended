package blended.streams.jms

import blended.jms.utils.{JmsAckSession, JmsDestination}
import blended.streams.message._
import blended.util.logging.Logger
import javax.jms._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try
import MsgProperty.Implicits._

case class JmsAcknowledgeHandler(
  jmsMessage : Message,
  session : JmsAckSession,
  created : Long = System.currentTimeMillis()
) extends AcknowledgeHandler {

  private val log = Logger[JmsAcknowledgeHandler]

  override def acknowledge: FlowEnvelope => Try[Unit] = { env => Try {
    log.debug(s"Acknowledging envelope [${env.id}]")
    session.ack(jmsMessage)
  }}
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
}

object JmsFlowSupport extends JmsEnvelopeHeader {

  import MsgProperty.lift

  // Convert a JMS message into a FlowMessage. This is normally used in JMS Sources
  val jms2flowMessage : (JmsSettings, Message) => Try[FlowMessage] = { (settings, msg) => Try {

    val prefix = settings.headerConfig.prefix

    val props: Map[String, MsgProperty[_]] = {

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

      val headers : Map[String, MsgProperty[_]] = Map(
        srcVendorHeader(prefix) -> srcVendor,
        srcProviderHeader(prefix) -> srcProvider,
        srcDestHeader(prefix) -> dest,
        priorityHeader(prefix) -> msg.getJMSPriority(),
        deliveryModeHeader(prefix) -> delMode
      )

      val expireHeaderMap : Map[String, MsgProperty[_]] = msg.getJMSExpiration() match {
        case 0L => Map.empty
        case v => Map(expireHeader(prefix) -> MsgProperty.lift(v).get)
      }

      val corrIdMap : Map[String, MsgProperty[_]] =
        Option(msg.getJMSCorrelationID()).map( s => corrIdHeader(prefix) -> MsgProperty.lift(s).get).toMap

      val props : Map[String, MsgProperty[_]] = msg.getPropertyNames().asScala.map { name =>
        (name.toString -> lift(msg.getObjectProperty(name.toString())).get)
      }.toMap

      val replyToMap : Map[String, MsgProperty[_]] =
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
  }}


  val envelope2jms : (JmsProducerSettings, Session, FlowEnvelope) => Try[JmsSendParameter] = (settings, session, flowEnv) =>  Try {

    settings.destinationResolver(settings).sendParameter(session, flowEnv).get

  }
}
