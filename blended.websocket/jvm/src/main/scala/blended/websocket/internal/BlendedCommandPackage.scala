package blended.websocket.internal

import akka.actor.ActorRef
import blended.security.login.api.Token
import blended.websocket.{WebSocketCommandHandler, WebSocketCommandPackage, WsContext}
import prickle._

class BlendedCommandPackage(
  override val namespace : String,
  override val handlerMgr : ActorRef
) extends WebSocketCommandPackage[String] {

  override def unpickler: Unpickler[String] = Unpickler.StringUnpickler

  private class VersionCommand(
    override val cmdPackage : WebSocketCommandPackage[String]
  ) extends WebSocketCommandHandler[String] {

    override val name : String = "version"
    override val description: String = "Return the blended version"

    override def doHandleCommand: PartialFunction[String, Token => WsContext] = {
      case n if n.equals(name) => t =>
        val r : WsContext = WsContext(namespace = namespace, name = name)
        emit("3.1-ui-SNAPSHOT", t, r)
        r
    }
  }

  override def commands: Seq[WebSocketCommandHandler[String]] = Seq(
    new VersionCommand(this)
  )
}
