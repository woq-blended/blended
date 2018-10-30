package blended.streams.message

import java.util.UUID

import blended.jms.utils.JmsAckSession
import blended.streams.message.FlowMessage.FlowMessageProps
import javax.jms.Message

import scala.beans.BeanProperty
import scala.util.Try

case class AckInfo(
  // TODO: Is dependency to JMS dependency correct
  jmsMessage : Message,
  session : JmsAckSession,
  created : Long = System.currentTimeMillis()
)

object FlowEnvelope {

  private def uuid() = UUID.randomUUID().toString()

  def apply() : FlowEnvelope = apply(FlowMessage.noProps)
  def apply(msg : FlowMessage) : FlowEnvelope = apply(msg, uuid())
  def apply(props: FlowMessageProps) : FlowEnvelope = apply(FlowMessage(props), uuid())

  def apply(props: FlowMessageProps, id : String) : FlowEnvelope = apply(FlowMessage(props), id)

  def apply(msg : FlowMessage, id : String) : FlowEnvelope = FlowEnvelope(
    flowMessage = msg,
    originalMessage = msg,
    id = id
  )
}

final case class FlowEnvelope private[message] (
  @BeanProperty
  flowMessage : FlowMessage,
  @BeanProperty
  originalMessage : FlowMessage,
  @BeanProperty
  exception :Option[Throwable] = None,
  @BeanProperty
  requiresAcknowledge : Boolean = false,
  @BeanProperty
  ackInfo : Option[AckInfo] = None,
  @BeanProperty
  id : String = UUID.randomUUID().toString(),
  flowContext : Map[String, Any] = Map.empty,
) {

  def header[T](key: String): Option[T] = flowMessage.header[T](key)
  def headerWithDefault[T](key: String, default : T) : T = flowMessage.headerWithDefault[T](key, default)

  def withHeader(key: String, value: Any, overwrite: Boolean = true) : Try[FlowEnvelope] = Try {
    copy(flowMessage = flowMessage.withHeader(key, value, overwrite).get)
  }

  def removeFromContext(key: String) : FlowEnvelope = copy(flowContext = flowContext.filter(_ != key))
  def withContextObject(key: String, o: Any) : FlowEnvelope = copy(flowContext = flowContext.filter(_ != key) + (key -> o))

  def getFromContext[T](key: String) : Try[Option[T]] = Try { flowContext.get(key).map(_.asInstanceOf[T]) }

  def clearException(): FlowEnvelope = copy(exception = None)
  def withException(t: Throwable): FlowEnvelope = copy(exception = Some(t))
  def withRequiresAcknowledge(b: Boolean): FlowEnvelope = copy(requiresAcknowledge = b)

  def withAckInfo(info : Option[AckInfo]) = copy(ackInfo = info)

  // For the default we simply do nothing when a downstream consumer calls acknowledge
  def acknowledge(): Unit = ackInfo.foreach { i => i.session.ack(i.jmsMessage) }
}
