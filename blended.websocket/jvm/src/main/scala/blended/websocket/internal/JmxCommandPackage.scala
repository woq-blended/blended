package blended.websocket.internal

import akka.actor.ActorSystem
import blended.jmx.BlendedMBeanServerFacade
import blended.security.login.api.Token
import blended.util.logging.Logger
import blended.websocket.{BlendedJmxMessage, JmxSubscribe, JmxUpdate, WebSocketCommandHandler, WebSocketCommandPackage, WsContext}
import blended.websocket.json.PrickleProtocol._
import prickle.Unpickler
import blended.websocket.WsUpdateEmitter.emit

import scala.util.{Failure, Success}

class JmxCommandPackage(
  override final val namespace : String = "jmx",
  jmxFacade : BlendedMBeanServerFacade
)(implicit system: ActorSystem) extends WebSocketCommandPackage {

  private val log : Logger = Logger[JmxCommandPackage]

  override type T = BlendedJmxMessage

  override def unpickler: Unpickler[BlendedJmxMessage] = jmxMessagesPicklerPair.unpickler

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
        val ctxt = WsContext(namespace, name)

        jmxFacade.mbeanNames(None) match {
          case Success(names) =>
            emit[BlendedJmxMessage](JmxUpdate(names = names, beans = Seq.empty), t, ctxt)(system)
          case Failure(e) =>
            log.warn(e)(s"Error getting MBean names : [${e.getMessage()}]")
        }
        ctxt
    }
  }
}

