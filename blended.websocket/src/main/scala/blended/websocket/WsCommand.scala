package blended.websocket

trait WsData[T] {
  // The namespacxe for the command
  def namespace : String
  // The command name
  def name : String
  // additional content, usually provided by subclasses via JSON
  def content : T

  case class WsDataEncoded(
    namespace : String,
    name : String,
    content : String
  )

}
