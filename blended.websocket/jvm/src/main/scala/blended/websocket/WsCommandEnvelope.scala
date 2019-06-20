package blended.websocket

import scala.util.Try

trait WsCommandEnvelope[T] {
  // The namespacxe for the command
  def namespace : String
  // The command name
  def name : String
  // additional content, usually provided by subclasses via JSON
  def content : T

  // Encode the content into a String
  def encode() : WsCommandEncoded
  // Decode the content from a String
  def decode(s : WsCommandEnvelope[T]) : Try[T]
}

case class WsCommandEncoded(
  namespace : String,
  name : String,
  content : String
)
