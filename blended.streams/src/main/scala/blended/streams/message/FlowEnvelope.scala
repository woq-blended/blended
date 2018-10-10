package blended.streams.message

import blended.jms.utils.JmsAckSession
import javax.jms.Message

sealed trait FlowEnvelope {

  def exception : Option[Throwable]
  def flowMessage : FlowMessage
  def requiresAcknowledge : Boolean
  def acknowledge() : Unit

  def clearException() : FlowEnvelope
  def withException(t : Throwable) : FlowEnvelope
}

final case class DefaultFlowEnvelope(
  override val flowMessage: FlowMessage,
  override val requiresAcknowledge : Boolean = false,
  override val exception: Option[Throwable] = None
) extends FlowEnvelope {

  override def clearException(): FlowEnvelope = copy(exception = None)
  override def withException(t: Throwable): FlowEnvelope = copy(exception = Some(t))

  // For the default we simply do nothing when a downstream consumer calls acknowledge
  override def acknowledge(): Unit = {}
}

final case class JmsAckEnvelope(
  override val flowMessage :FlowMessage,
  override val requiresAcknowledge : Boolean = true,
  jmsMessage: Message,
  session: JmsAckSession,
  created: Long,
  override val exception : Option[Throwable] = None
) extends FlowEnvelope {

  override def acknowledge(): Unit = session.ack(jmsMessage)

  override def clearException(): FlowEnvelope = copy(exception = None)
  override def withException(t: Throwable): FlowEnvelope = copy(exception = Some(t))
}
