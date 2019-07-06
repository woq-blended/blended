package blended.websocket.internal

import akka.actor.{Actor, ActorRef, Props, Terminated}
import blended.security.login.api.Token
import blended.util.logging.Logger
import blended.websocket.{WithKey, WsContext}
import blended.websocket.WsUpdateEmitter.emit
import prickle.Pickler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

case class WebsocketSubscription[T <: WithKey](
  context : WsContext,
  token : Token,
  interval : Option[FiniteDuration],
  pickler : Pickler[T],
  cmd : T,
  update : PartialFunction[T,T]
)

case class NewSubscription[T <: WithKey](sub : WebsocketSubscription[T])
case class RemoveSubscription[T <: WithKey](sub : WebsocketSubscription[T])

/**
  * Manage all subscriptions for currently connected web socket clients.
  */
class WebSocketSubscriptionManager extends Actor {

  private val log : Logger = Logger[WebSocketSubscriptionManager]

  private val subKey : WebsocketSubscription[_] => String =  { s =>
    val userKey : String = s.asInstanceOf[WithKey].key
    s"${s.context.namespace}:${s.context.name}:${userKey}:${s.token.user.getName()}"
  }

  override def preStart(): Unit = {
    super.preStart()
    context.become(handling(Map.empty))
  }

  override def receive: Receive = Actor.emptyBehavior

  private def handling( subscriptions : Map[String, ActorRef]) : Receive = {

    case NewSubscription(sub) =>
      val key : String = subKey(sub)
      if (!subscriptions.contains(key)) {
        log.info(s"Creating subscription actor for [${key}]")
        val actor : ActorRef = context.actorOf(WebSocketSubscriptionActor.props(sub))
        context.watch(actor)
        context.become(handling(subscriptions + (key -> actor)))
      } else {
        log.debug(s"Subscription [$key] is already active.")
      }

    case RemoveSubscription(sub) =>
      val key : String = subKey(sub)
      subscriptions.get(key).foreach{ a =>
        log.info(s"Removing subscription [$key]")
        context.unwatch(a)
        context.stop(a)
      }
      context.become(handling(subscriptions.filterKeys(_ != key)))

    case Terminated(a) =>
      subscriptions.find { case (_,sa) => a == sa }.foreach { case (sk, sa) =>
        log.warn(s"subscription actor for [$sk] terminated, removing subscription.")
        context.become(handling(subscriptions.filterKeys(_ != sk)))
      }
  }

}

object WebSocketSubscriptionActor {

  def props[T <: WithKey](sub : WebsocketSubscription[T]) : Props =
    Props(new WebSocketSubscriptionActor[T](sub))
}

class WebSocketSubscriptionActor[T <: WithKey](subscription : WebsocketSubscription[T]) extends Actor {

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
          emit[T](
            msg = upd, token = subscription.token, context = subscription.context, pickler = subscription.pickler
          )(context.system)
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
