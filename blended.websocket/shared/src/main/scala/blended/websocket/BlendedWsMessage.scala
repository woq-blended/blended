package blended.websocket

trait WithKey {
  def key : String = ""
}

sealed trait BlendedWsMessage extends WithKey
case class Version() extends BlendedWsMessage
case class VersionResponse(v : String) extends BlendedWsMessage
