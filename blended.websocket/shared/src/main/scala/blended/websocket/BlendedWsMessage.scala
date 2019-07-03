package blended.websocket

sealed trait BlendedWsMessage
case class Version() extends BlendedWsMessage
case class VersionResponse(v : String) extends BlendedWsMessage
