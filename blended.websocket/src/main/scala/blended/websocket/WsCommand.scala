package blended.websocket

trait WsData {
  // The namespacxe for the command
  def namespace : String
  // The command name
  def name : String
  // additional content, usually provided by subclasses via JSON
  def content : String
}


