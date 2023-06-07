package blended.streams.jms

import blended.jms.utils.JmsDestination
import blended.streams.FlowHeaderConfig
import blended.streams.message._
import blended.util.logging.Logger
import javax.jms.{Destination, JMSException, Message, Session}

import scala.concurrent.duration._
import scala.util.{Success, Try}

trait JmsDestinationResolver { this : JmsEnvelopeHeader =>

  def settings : JmsProducerSettings
  def correlationId(env : FlowEnvelope) : Option[String]
  def sendParameter(session : Session, env : FlowEnvelope) : Try[JmsSendParameter]
  def replyTo(session : Session, env : FlowEnvelope) : Try[Option[Destination]]

  def createJmsMessage(session : Session, env : FlowEnvelope) : Try[Message] = Try {

    val flowMsg = env.flowMessage

    val msg = flowMsg match {
      case t :
        // scalastyle:off null
        TextFlowMessage => session.createTextMessage(Option(t.body()).map(_.toString).orNull)
      // scalastyle:on null
      case b :
        BinaryFlowMessage =>
        val r = session.createBytesMessage()
        r.writeBytes(b.getBytes().toArray)

        r
      case _ => session.createMessage()
    }

    flowMsg.header.filter {
      case (k, _) => !k.startsWith("JMS")
    }.foreach {
      case (k, v) =>
        val propName = settings.keyFormatStrategy.handleSendKey(k)
        v match {
          // scalastyle:off null
          case _ : UnitMsgProperty => msg.setObjectProperty(propName, null)
          case o                   => msg.setObjectProperty(propName, o.value)
          // scalastyle:on null
        }
    }

    // Always set the env id as id for the message
    msg.setStringProperty(settings.headerCfg.headerTransId, env.id)

    replyTo(session, env).get.fold(
      msg.setJMSReplyTo(null)
    )(d => msg.setJMSReplyTo(d))
    // Always try to get the CorrelationId from the flow Message
    correlationId(env).foreach(msg.setJMSCorrelationID)

    msg
  }
}

trait FlowHeaderConfigAware extends JmsDestinationResolver {
  this : JmsEnvelopeHeader =>

  val log : Logger = Logger(getClass().getName())

  def headerConfig : FlowHeaderConfig = settings.headerCfg

  override def correlationId(env : FlowEnvelope) : Option[String] = {
    env.header[String](corrIdHeader(headerConfig.prefix)) match {
      case None => env.header[String]("JMSCorrelationID")
      case x    => x
    }
  }

  override def replyTo(session : Session, env : FlowEnvelope) : Try[Option[Destination]] = Try {
    env.header[String](replyToHeader(headerConfig.prefix)) match {
      case None    => None
      case Some(d) => Some(JmsDestination.create(d).map(dest => dest.create(session)).get)
    }
  }

  // Get the destination from the message
  def destination(env : FlowEnvelope) : Try[JmsDestination] = {

    val flowMsg : FlowMessage = env.flowMessage

    Try {

      log.trace(s"Trying to resolve destination for [${env.id}] from header [${destHeader(headerConfig.prefix)}]")

      val d = flowMsg.header[String](s"${destHeader(headerConfig.prefix)}") match {
        case Some(s) =>
          JmsDestination.create(s).get

        case None =>
          log.trace(s"Trying to resolve destination for [${env.id}] from settings.")
          settings.jmsDestination match {
            case Some(dest) => dest
            case None =>
              throw new JMSException(s"Could not resolve JMS destination for [${env.id}]")
          }
      }

      log.debug(s"Resolved destination for [${env.id}] to [${d.asString}]")
      d
    }
  }

  val priority : FlowMessage => Int = { flowMsg =>
    flowMsg.header[Int](priorityHeader(headerConfig.prefix)) match {
      case Some(p) => p
      case None    => settings.priority
    }
  }

  val timeToLive : FlowMessage => Option[FiniteDuration] = { flowMsg =>
    flowMsg.header[Long](expireHeader(headerConfig.prefix)) match {
      case Some(l) => Some(Math.max(1L, l - System.currentTimeMillis()).millis)
      case None    => settings.timeToLive
    }
  }

  val deliveryMode : FlowMessage => JmsDeliveryMode = { flowMsg =>
    flowMsg.header[String](deliveryModeHeader(headerConfig.prefix)) match {
      case Some(s) => JmsDeliveryMode.create(s).get
      case None    => settings.deliveryMode
    }
  }
}

class SettingsDestinationResolver(
  override val settings : JmsProducerSettings
)
  extends JmsDestinationResolver
  with JmsEnvelopeHeader {

  override def correlationId(env : FlowEnvelope) : Option[String] =
    env.header[String]("JMSCorrelationID")

  override def replyTo(session : Session, env : FlowEnvelope) : Try[Option[Destination]] = Success(None)

  override def sendParameter(session : Session, env : FlowEnvelope) : Try[JmsSendParameter] = Try {

    val msg : Message = createJmsMessage(session, env).get
    // Get the destination
    val dest : JmsDestination = settings.jmsDestination match {
      case Some(d) => d
      case None    => throw new JMSException(s"Could not resolve JMS destination for [$env]")
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
  override val settings : JmsProducerSettings
)
  extends FlowHeaderConfigAware
  with JmsEnvelopeHeader {

  override def sendParameter(session : Session, env : FlowEnvelope) : Try[JmsSendParameter] = Try {

    val flowMsg = env.flowMessage
    val msg = createJmsMessage(session, env).get

    // Get the destination
    val dest : JmsDestination = destination(env).get

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
