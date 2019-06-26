package blended.websocket

sealed trait BlendedWsMessages
case class Version() extends BlendedWsMessages
case class VersionResponse(v : String) extends BlendedWsMessages
