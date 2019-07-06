package blended.websocket.internal

import akka.actor.ActorSystem
import blended.jmx.{BlendedMBeanServerFacade, JmxBeanInfo, JmxObjectName}
import blended.security.login.api.Token
import blended.util.RichTry._
import blended.util.logging.Logger
import blended.websocket._
import blended.websocket.json.PrickleProtocol._
import prickle.{Pickler, Unpickler}

import scala.concurrent.duration._
import scala.util.{Failure, Try}

object JmxCommandPackage {

  private val log : Logger = Logger[JmxCommandPackage.type]

  val jmxUpdate : BlendedMBeanServerFacade => PartialFunction[BlendedJmxMessage, Try[BlendedJmxMessage]] = mbf => {
    case s : JmxSubscribe => Try {
      log.info(s"Performing Jmx update for [${s.objName}]")

      val allNames: List[JmxObjectName] = mbf.allMbeanNames().unwrap

      val infos: List[JmxBeanInfo] = s.objName match {
        case None => List.empty
        case Some(name) =>
          mbf.mbeanNames(Some(name)).unwrap.map { n =>
            mbf.mbeanInfo(n).unwrap
          }
      }

      JmxUpdate(allNames, infos)
    }

    case m =>
      val msg : String = s"Update called with invalid object : [$m]"
      Failure(new Exception(msg))
  }
}

class JmxCommandPackage(
  override final val namespace : String = "jmx",
  jmxFacade : BlendedMBeanServerFacade
)(implicit system: ActorSystem) extends WebSocketCommandPackage {

  private val log : Logger = Logger[JmxCommandPackage]
  private val subscribeCmdName : String = "subscribe"

  override type T = BlendedJmxMessage

  override def unpickler: Unpickler[BlendedJmxMessage] = jmxMessagePicklerPair.unpickler

  override def commands: Seq[WebSocketCommandHandler[BlendedJmxMessage]] = Seq(
    new SubscriptionHandler(this),
    new UnsubscriptionHandler(this)
  )

  private val createSubscription : Token => JmxSubscribe => WebSocketSubscription = t => s => new WebSocketSubscription {
    override type T = BlendedJmxMessage

    override def context: WsContext = WsContext(namespace, subscribeCmdName)
    override def token: Token = t
    override def interval: Option[FiniteDuration] = if (s.intervalMS <= 0) None else Some(s.intervalMS.millis)
    override def pickler: Pickler[BlendedJmxMessage] = jmxMessagePicklerPair.pickler
    override def cmd: BlendedJmxMessage = s
    override def update: PartialFunction[BlendedJmxMessage, Try[BlendedJmxMessage]] = JmxCommandPackage.jmxUpdate(jmxFacade)
  }

  private class SubscriptionHandler(
    override val cmdPackage : WebSocketCommandPackage
  ) extends WebSocketCommandHandler[T] {

    override val name: String = "subscribe"
    override val description : String = "Subscribe to Jmx updates"

    override def doHandleCommand: PartialFunction[BlendedJmxMessage, Token => WsContext] = {
      case s : JmxSubscribe => t =>
        val subscription : WebSocketSubscription = createSubscription(t)(s)
        system.eventStream.publish(NewSubscription(subscription))
        log.debug(s"Published new [$subscription] to event stream")
        subscription.context
    }
  }

  private class UnsubscriptionHandler(
    override val cmdPackage : WebSocketCommandPackage
  ) extends WebSocketCommandHandler[T] {

    override val name: String = "unsubscribe"
    override val description : String = "Unsubscribe from Jmx updates"

    override def doHandleCommand: PartialFunction[BlendedJmxMessage, Token => WsContext] = {
      case JmxUnsubscribe(u) => t =>
        val subscription : WebSocketSubscription = createSubscription(t)(u)
        system.eventStream.publish(RemoveSubscription(subscription))
        log.debug(s"Published remove [$subscription] to event stream")
        subscription.context
    }
  }
}

