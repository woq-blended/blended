package blended.websocket

case class WsUnitMessage(override val context: WsMessageContext) extends WsMessageEnvelope[Unit] {
  override def content: Unit = ()
}

case class WsStringMessage(
  override val context : WsMessageContext,
  override val content : String
) extends WsMessageEnvelope[String]
