package blended.websocket.internal

import java.util.Properties

import akka.actor.ActorSystem
import blended.security.login.api.Token
import blended.websocket.json.PrickleProtocol._
import blended.websocket._
import prickle._

object BlendedCommandPackage {

  val version : String = {
    val props : Properties = new Properties()
    props.load(getClass().getResourceAsStream("version.properties"))
    Option(props.get("version")).map(_.toString).getOrElse("")
  }
}

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
        val ctxt : WsContext = WsContext(namespace = namespace, name = name)
        // This is a side effect, which will push the version info to the client
        emit(VersionResponse(BlendedCommandPackage.version), t, ctxt)
        ctxt
    }
  }

  override def commands: Seq[WebSocketCommandHandler[T]] = Seq(
    new VersionCommand(this)
  )
}