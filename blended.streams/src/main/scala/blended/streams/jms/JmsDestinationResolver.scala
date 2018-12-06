package blended.streams.jms

import blended.jms.utils.JmsDestination
import blended.streams.message.{BinaryFlowMessage, FlowEnvelope, FlowMessage, TextFlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger
import javax.jms.{JMSException, Message, Session}

import scala.concurrent.duration._
import scala.util.Try

trait JmsDestinationResolver { this : JmsEnvelopeHeader =>

  def settings : JmsProducerSettings
  def correlationId(env : FlowEnvelope) : Option[String]
  def sendParameter(session: Session, env: FlowEnvelope) : Try[JmsSendParameter]

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

    // Always try to get the CorrelationId from the flow Message
    correlationId(env).foreach(msg.setJMSCorrelationID)

    msg
  }
}

trait FlowHeaderConfigAware extends JmsDestinationResolver {
  this: JmsEnvelopeHeader =>

  val log : Logger = Logger[JmsDestinationResolver]

  def headerConfig: FlowHeaderConfig

  override def correlationId(env: FlowEnvelope): Option[String] = {
    env.header[String](corrIdHeader(headerConfig.prefix)) match {
      case None => env.header[String]("JMSCorrelationID")
      case x => x
    }
  }

  // Get the destination from the message
  val destination: FlowMessage => Try[JmsDestination] = { flowMsg =>
    Try {
      val d = flowMsg.header[String](s"${destHeader(headerConfig.prefix)}") match {
        case Some(s) => JmsDestination.create(s).get
        case None => settings.jmsDestination match {
          case Some(d) => d
          case None =>
            throw new JMSException(s"Could not resolve JMS destination for [$flowMsg]")
        }
      }

      log.trace(s"Resolved destination for [${flowMsg.header[String](headerConfig.headerTrans)}] to [${d.asString}]")
      d
    }
  }

  val priority: FlowMessage => Int = { flowMsg =>
    flowMsg.header[Int](priorityHeader(headerConfig.prefix)) match {
      case Some(p) => p
      case None => settings.priority
    }
  }

  val timeToLive: FlowMessage => Option[FiniteDuration] = { flowMsg =>
    flowMsg.header[Long](expireHeader(headerConfig.prefix)) match {
      case Some(l) => Some((Math.max(1L, l - System.currentTimeMillis())).millis)
      case None => settings.timeToLive
    }
  }

  val deliveryMode: FlowMessage => JmsDeliveryMode = { flowMsg =>
    flowMsg.header[String](deliveryModeHeader(headerConfig.prefix)) match {
      case Some(s) => JmsDeliveryMode.create(s).get
      case None => settings.deliveryMode
    }
  }
}

class SettingsDestinationResolver(
  override val settings: JmsProducerSettings
)
  extends JmsDestinationResolver
  with JmsEnvelopeHeader {


  override def correlationId(env: FlowEnvelope): Option[String] =
    env.header[String]("JMSCorrelationID")

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

class MessageDestinationResolver(
  override val headerConfig : FlowHeaderConfig,
  override val settings: JmsProducerSettings
)
  extends FlowHeaderConfigAware
  with JmsEnvelopeHeader {

  private val prefix = headerConfig.prefix

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
