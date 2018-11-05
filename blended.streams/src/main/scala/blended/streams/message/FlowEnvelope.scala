package blended.streams.message

import java.util.UUID

import blended.streams.message.FlowMessage.FlowMessageProps

import scala.beans.BeanProperty
import scala.util.Try

trait AcknowledgeHandler {
  def acknowledge : FlowEnvelope => Try[Unit]
}

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
  ackHandler : Option[AcknowledgeHandler] = None,
  @BeanProperty
  id : String = UUID.randomUUID().toString(),
  flowContext : Map[String, Any] = Map.empty,
) {

  def header[T](key: String): Option[T] = flowMessage.header[T](key)
  def headerWithDefault[T](key: String, default : T) : T = flowMessage.headerWithDefault[T](key, default)

  def withHeaders(header: FlowMessageProps) : Try[FlowEnvelope] = Try {
    copy(flowMessage = flowMessage.withHeaders(header).get)
  }

  def withHeader(key: String, value: Any, overwrite: Boolean = true) : Try[FlowEnvelope] = Try {
    copy(flowMessage = flowMessage.withHeader(key, value, overwrite).get)
  }

  def removeFromContext(key: String) : FlowEnvelope = copy(flowContext = flowContext.filter(_ != key))
  def withContextObject(key: String, o: Any) : FlowEnvelope = copy(flowContext = flowContext.filter(_ != key) + (key -> o))

  def getFromContext[T](key: String) : Try[Option[T]] = Try { flowContext.get(key).map(_.asInstanceOf[T]) }

  def clearException(): FlowEnvelope = copy(exception = None)
  def withException(t: Throwable): FlowEnvelope = copy(exception = Some(t))
  def withRequiresAcknowledge(b: Boolean): FlowEnvelope = copy(requiresAcknowledge = b)

  def withAckHandler(handler : Option[AcknowledgeHandler]) = copy(ackHandler = handler)

  // For the default we simply do nothing when a downstream consumer calls acknowledge
  def acknowledge(): Unit = ackHandler.foreach(h => h.acknowledge(this))
}