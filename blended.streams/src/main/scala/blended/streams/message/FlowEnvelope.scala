package blended.streams.message

import blended.jms.utils.JmsAckSession
import javax.jms.{Message, Session}

abstract class FlowEnvelope(msg: FlowMessage) {

  def acknowledge() : Unit

}

final case class DefaultFlowEnvelope(msg: FlowMessage) extends FlowEnvelope(msg) {

  // For the default we simply do nothing when a downstream consumer calls acknowledge
  override def acknowledge(): Unit = {}
}

final case class JmsAckEnvelope(flowMsg: FlowMessage, jmsMsg: Message, session: JmsAckSession) extends FlowEnvelope(flowMsg) {

  // TODO define acknowledge
  override def acknowledge(): Unit = ???
}
