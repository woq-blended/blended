package blended.websocket

import akka.http.scaladsl.model.ws.TextMessage
import blended.websocket.json.PrickleProtocol._
import prickle._

import scala.util.Try

object JsonHelper {

  def decode[T](s : String)(implicit up: Unpickler[T]) : Try[T] = Try {
    Unpickle[T].fromString(s).get
  }

  def encode[T](obj : T)(implicit p : Pickler[T]) : String = Pickle.intoString(obj)
}

case class WsResult(
  namespace : String,
  name : String,
  status : Int = 200,
  statusMsg : Option[String] = None
)

object WsMessageEncoded {

  def fromResult(result : WsResult) : TextMessage.Strict = fromObject(result, ())

  def fromObject[T](result: WsResult, t : T)(implicit p:  Pickler[T]) : TextMessage.Strict = {
    TextMessage.Strict(
      Pickle.intoString(WsMessageEncoded(
      result = result, content = Pickle.intoString(t)
    )))
  }
}

case class WsMessageEncoded(
  result : WsResult,
  // The payload - either command parameters sent by the client or the message
  // sent to a client
  content : String
)
