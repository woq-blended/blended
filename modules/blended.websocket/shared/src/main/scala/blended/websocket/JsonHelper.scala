package blended.websocket

import java.util.Base64

import blended.websocket.json.PrickleProtocol._
import prickle._

import scala.util.Try

object JsonHelper {

  def decode[T](s : String)(implicit up: Unpickler[T]) : Try[T] = Try {
    Unpickle[T].fromString(s).get
  }

  def encode[T](obj : T)(implicit p : Pickler[T]) : String = Pickle.intoString(obj)
}

// scalastyle:off magic.number
case class WsContext(
  namespace : String,
  name : String,
  status : Int = 200,
  statusMsg : Option[String] = None
)
// scalastyle:on magic.number

object WsMessageEncoded {

  def fromContext(context : WsContext) : WsMessageEncoded = fromObject(context, ())

  def fromObject[T](context: WsContext, t : T)(implicit p:  Pickler[T]) : WsMessageEncoded = {
    val b64 : String = Base64.getEncoder().encodeToString(Pickle.intoString(t).getBytes())
    WsMessageEncoded(
      context = context, content = b64
    )
  }

  def fromCommand(namespace : String, name: String) : WsMessageEncoded = fromContext(WsContext(namespace, name))
}

case class WsMessageEncoded(
  context : WsContext,
  // The payload - either command parameters sent by the client or the message
  // sent to a client
  content : String
) {

  val json : String = Pickle.intoString(this)

  def decode[T](implicit up : Unpickler[T]) : Try[T] = {
    val json : String = new String(Base64.getDecoder().decode(content))
    Unpickle[T].fromString(json)
  }
}
