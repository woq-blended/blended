package blended.streams.message

import blended.jms.utils.JmsAckSession
import javax.jms.Message

case class AckInfo(
  jmsMessage : Message,
  session : JmsAckSession,
  created : Long = System.currentTimeMillis()
)

final case class FlowEnvelope(
  flowMessage : FlowMessage,
  exception :Option[Throwable] = None,
  requiresAcknowledge : Boolean = false,
  ackInfo : Option[AckInfo] = None
) {
  def clearException(): FlowEnvelope = copy(exception = None)
  def withException(t: Throwable): FlowEnvelope = copy(exception = Some(t))
  def withRequiresAcknowledge(b: Boolean): FlowEnvelope = copy(requiresAcknowledge = b)

  // For the default we simply do nothing when a downstream consumer calls acknowledge
  def acknowledge(): Unit = ackInfo.foreach { i => i.session.ack(i.jmsMessage) }
}
