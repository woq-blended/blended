package blended.websocket

import java.util.Base64

import akka.http.scaladsl.model.StatusCode
import blended.websocket.json.PrickleProtocol._
import prickle._

import scala.util.Try

case class WsMessageContext(
  namespace : String,
  name : String,
  status : Option[Int] = None,
  statusMsg : Option[String] = None
) {
  def withStatus(s : StatusCode) : WsMessageContext = withStatus(s.intValue())
  def withStatus(s : StatusCode, m : String) : WsMessageContext = withStatus(s.intValue(), m)
  def withStatus(s : Int) : WsMessageContext = copy(status = Some(s), statusMsg = None)
  def withStatus(s : Int, m : String) : WsMessageContext = copy(status = Some(s), statusMsg = Some(m))
}

object WsMessageEnvelope {

  def decode[T](s : String)(implicit up: Unpickler[T]) : Try[(WsMessageContext, T)] = Try {
    val encoded : WsMessageEncoded = Unpickle[WsMessageEncoded].fromString(s).get
    val json : String = new String(Base64.getDecoder().decode(encoded.content))
    val v : T = Unpickle[T].fromString(json).get

    (encoded.context, v)
  }
}

trait WsMessageEnvelope[T] {

  def context : WsMessageContext
  def content : T

  /**
    * Encode the WsMessage into a JSON encoded envelope
    */
  def encode(
    status : Option[Int] = context.status,
    statusMsg: Option[String] = context.statusMsg
  )(implicit p : Pickler[T]) : String = {

    val json : String = Pickle.intoString(content)

    val encoded : WsMessageEncoded = WsMessageEncoded(
      context = context.copy(status = status, statusMsg = statusMsg),
      content = Base64.getEncoder().encodeToString(json.getBytes())
    )

    Pickle.intoString(encoded)
  }
}

case class WsMessageEncoded(
  // The message context
  context : WsMessageContext,
  // The payload - either command parameters sent by the client or the message
  // sent to a client
  content : String
)
