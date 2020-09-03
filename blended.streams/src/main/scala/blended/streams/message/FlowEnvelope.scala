package blended.streams.message

import java.util.UUID

import blended.streams.message.FlowMessage.FlowMessageProps

import scala.beans.BeanProperty
import scala.util.Try

trait AcknowledgeHandler {
  def acknowledge() : Try[Unit]
  def deny() : Try[Unit]
}

object FlowEnvelope {

  private def uuid() = UUID.randomUUID().toString()

  def apply() : FlowEnvelope = apply(FlowMessage.noProps)
  def apply(msg : FlowMessage) : FlowEnvelope = apply(msg, uuid())
  def apply(props : FlowMessageProps) : FlowEnvelope = apply(FlowMessage(props), uuid())

  def apply(props : FlowMessageProps, id : String) : FlowEnvelope = apply(FlowMessage(props), id)

  def apply(msg : FlowMessage, id : String) : FlowEnvelope = FlowEnvelope(
    flowMessage = msg,
    originalMessage = msg,
    id = id
  )
}

final case class FlowEnvelope private[message] (
  @BeanProperty flowMessage : FlowMessage,
  @BeanProperty originalMessage : FlowMessage,
  @BeanProperty exception : Option[Throwable] = None,
  @BeanProperty requiresAcknowledge : Boolean = false,
  @BeanProperty ackHandler : Option[AcknowledgeHandler] = None,
  @BeanProperty id : String = UUID.randomUUID().toString(),
  flowContext : Map[String, Any] = Map.empty
) {

  override def toString : String = {

    val err : String = exception.map(t => s"[exception=${t.getMessage}]").getOrElse("")
    val ctxtKeys : String = flowContext match {
      case e if e.isEmpty => ""
      case m              => s"[context keys=${m.keys.mkString(",")}]"
    }
    s"FlowEnvelope[$id][$requiresAcknowledge][$flowMessage]$ctxtKeys$err"
  }

  def withId(newId : String) : FlowEnvelope = copy(id = newId)

  def header[T](key : String)(implicit m : Manifest[T]) : Option[T] = flowMessage.header[T](key)
  def headerWithDefault[T](key : String, default : T)(implicit m : Manifest[T]) : T = flowMessage.headerWithDefault[T](key, default)

  def withHeaders(header : FlowMessageProps) : Try[FlowEnvelope] = Try {
    copy(flowMessage = flowMessage.withHeaders(header).get)
  }

  def withHeader(key : String, value : Any, overwrite : Boolean = true) : Try[FlowEnvelope] = Try {
    copy(flowMessage = flowMessage.withHeader(key, value, overwrite).get)
  }

  def removeHeader(keys : String*) : FlowEnvelope = copy(flowMessage.removeHeader(keys : _*))

  def removeFromContext(key : String) : FlowEnvelope = copy(flowContext = flowContext.view.filterKeys(_ != key).toMap)
  def withContextObject(key : String, o : Any) : FlowEnvelope = copy(flowContext = flowContext.view.filterKeys(_ != key).toMap + (key -> o))

  def getFromContext[T](key : String) : Try[Option[T]] = Try { flowContext.get(key).map(_.asInstanceOf[T]) }

  def clearException() : FlowEnvelope = copy(exception = None)
  def withException(t : Throwable) : FlowEnvelope = copy(exception = Some(t))
  def withRequiresAcknowledge(b : Boolean) : FlowEnvelope = copy(requiresAcknowledge = b)

  def withAckHandler(handler : Option[AcknowledgeHandler]) : FlowEnvelope = copy(ackHandler = handler)

  // For the default we simply do nothing when a downstream consumer calls acknowledge or deny
  def acknowledge() : Unit = { ackHandler.foreach(h => h.acknowledge()) }

  def deny() : Unit = ackHandler.foreach(h => h.deny())
}
