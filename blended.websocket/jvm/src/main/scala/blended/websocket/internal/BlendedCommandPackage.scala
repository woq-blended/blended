package blended.websocket.internal

import akka.actor.ActorSystem
import blended.security.login.api.Token
import blended.websocket.json.PrickleProtocol._
import blended.websocket._
import prickle._

class BlendedCommandPackage(
  override val namespace : String
)(implicit system: ActorSystem) extends WebSocketCommandPackage {

  override type T = BlendedWsMessages

  override def unpickler: Unpickler[T] = wsMessagesPicklerPair.unpickler

  private class VersionCommand(
    override val cmdPackage : WebSocketCommandPackage
  ) extends WebSocketCommandHandler[T] {

    override val name : String = "version"
    override val description: String = "Return the blended version"

    override def doHandleCommand: PartialFunction[BlendedWsMessages, Token => WsContext] = {
      case _ : Version => t =>
        val r : WsContext = WsContext(namespace = namespace, name = name)
        emit(VersionResponse("3.1-ui-SNAPSHOT"), t, r)
        r
    }
  }

  override def commands: Seq[WebSocketCommandHandler[T]] = Seq(
    new VersionCommand(this)
  )
}
