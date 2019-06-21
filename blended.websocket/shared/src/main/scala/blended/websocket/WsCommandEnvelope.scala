package blended.websocket

import java.util.Base64

import prickle._

import scala.util.Try

trait WsCommandEnvelope[T] {

  case class WsCommand(
    namespace : String,
    name : String,
    content : T,
    status : Option[Int] = None,
    statusMsg : Option[String] = None
  ) {
    def withStatus(code : Int) : WsCommand = copy(
      status = Some(code),
      statusMsg = None
    )

    def withStatus(code : Int, msg : String) : WsCommand = copy(
      status = Some(code),
      statusMsg = Some(msg)
    )
  }

  // Encode the content into a String
  def encode(cmd : WsCommand)(implicit p : Pickler[T]) : WsMessageEncoded = {

    val json : String = Pickle.intoString(cmd.content)

    WsMessageEncoded(
      namespace = cmd.namespace,
      name = cmd.name,
      content = Base64.getEncoder().encodeToString(json.getBytes()),
      status = cmd.status,
      statusMsg = cmd.statusMsg
    )
  }
  // Decode the content from a n encoded envelope
  def decode(s : WsMessageEncoded)(implicit u : Unpickler[T]) : Try[WsCommand] = Try {

    val json : String = new String(Base64.getDecoder().decode(s.content))
    val v : T = Unpickle[T].fromString(json).get

    WsCommand(
      namespace = s.namespace,
      name = s.name,
      status = s.status,
      statusMsg = s.statusMsg,
      content = v
    )
  }
}

case class WsMessageEncoded(
  // The namespace for a command
  namespace : String,
  // The name of the command
  name : String,
  // In case of a response messge, an int equivalent to a HTTP status code
  status : Option[Int],
  // In case of a response, an optional result message
  statusMsg : Option[String],
  // The payload - either command parameters sent by the client or the message
  // sent to a client
  content : String
)
