package blended.streams.message

import blended.jms.utils.JmsAckSession
import javax.jms.Message

abstract class FlowEnvelope(m: FlowMessage) {

  def flowMessage : FlowMessage = m
  def acknowledge() : Unit
}

final case class DefaultFlowEnvelope(msg: FlowMessage) extends FlowEnvelope(msg) {

  // For the default we simply do nothing when a downstream consumer calls acknowledge
  override def acknowledge(): Unit = {}
}

final case class JmsAckEnvelope( msg: FlowMessage, jmsMsg: Message, session: JmsAckSession, created: Long) extends FlowEnvelope(msg) {

  override def acknowledge(): Unit = session.ack(jmsMsg)
}
