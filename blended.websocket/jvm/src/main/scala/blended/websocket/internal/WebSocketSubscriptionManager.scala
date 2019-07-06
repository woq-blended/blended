package blended.websocket.internal

import akka.actor.{Actor, Props}
import blended.security.login.api.Token
import blended.util.logging.Logger
import blended.websocket.WsContext
import blended.websocket.WsUpdateEmitter.emit
import prickle.Pickler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

case class WebsocketSubscription[T](
  context : WsContext,
  token : Token,
  interval : Option[FiniteDuration],
  cmd : T,
  update : PartialFunction[T,T]
)

/**
  * Manage all subscriptions for currently connected web socket clients.
  */
class WebSocketSubscriptionManager extends Actor {

  override def receive: Receive = Actor.emptyBehavior

}

object WebSocketSubscriptionActor {

  def props[T](sub : WebsocketSubscription[T])(implicit pickler : Pickler[T]) : Props =
    Props(new WebSocketSubscriptionActor[T](sub))
}

class WebSocketSubscriptionActor[T](subscription : WebsocketSubscription[T])(implicit pickler : Pickler[T]) extends Actor {

  private val log : Logger = Logger[WebSocketSubscriptionActor.type]
  private implicit val eCtxt : ExecutionContext = context.system.dispatcher
  private case object Tick

  override def preStart(): Unit = {
    super.preStart()
    self ! Tick
  }


  override def postStop(): Unit = {
    log.info(s"Subscription for [${subscription.context}][${subscription.cmd}], user [${subscription.token.user}] ended.")
    super.postStop()
  }

  override def receive: Receive = {
    case Tick =>
      val user : String = subscription.token.user.getName()
      log.debug(s"Evaluating subscription command [${subscription.cmd}] for user [$user]")
      subscription.update.lift(subscription.cmd) match {
        case Some(upd) =>
          log.debug(s"Got subscription update for user [$user] : [$upd]")
          emit[T](upd, subscription.token, subscription.context)(context.system)
          subscription.interval match {
            case None => context.stop(self)
            case Some(t) => context.system.scheduler.scheduleOnce(t, self, Tick)
          }
        case None =>
          log.warn(s"Subscription function is not defined at [${subscription.cmd}] for user [$user], subscription will terminate.")
          context.stop(self)
      }
  }
}
