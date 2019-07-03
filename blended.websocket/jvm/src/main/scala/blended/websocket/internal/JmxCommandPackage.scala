package blended.websocket.internal

import akka.actor.ActorSystem
import blended.jmx.BlendedMBeanServerFacade
import blended.security.login.api.Token
import blended.websocket.{BlendedJmxMessage, JmxSubscribe, WebSocketCommandHandler, WebSocketCommandPackage, WsContext}
import blended.websocket.json.PrickleProtocol
import prickle.Unpickler

class JmxCommandPackage(
  override final val namespace : String = "jmx",
  jmxFacade : BlendedMBeanServerFacade
)(implicit system: ActorSystem) extends WebSocketCommandPackage {

  override type T = BlendedJmxMessage

  override def unpickler: Unpickler[BlendedJmxMessage] = PrickleProtocol.jmxMessagesPicklerPair.unpickler

  override def commands: Seq[WebSocketCommandHandler[BlendedJmxMessage]] = Seq(
    new SubscriptionHandler(this)
  )

  private class SubscriptionHandler(
    override val cmdPackage : WebSocketCommandPackage
  ) extends WebSocketCommandHandler[T] {

    override val name: String = "subscribe"

    override val description : String = "Subscribe to Jmx updates"

    override def doHandleCommand: PartialFunction[BlendedJmxMessage, Token => WsContext] = {
      case _ : JmxSubscribe => t =>
        WsContext(namespace, name)
    }
  }
}

