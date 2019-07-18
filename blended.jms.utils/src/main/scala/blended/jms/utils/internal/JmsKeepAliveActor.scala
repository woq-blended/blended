package blended.jms.utils.internal

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import blended.jms.utils.KeepAlive
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object JmsKeepAliveActor {
  def props(
    vendor : String,
    provider : String,
    interval: FiniteDuration,
    maxKeepAlivesMissed : Int,
    keepAliveActor: ActorRef,
    reconnectTarget : ActorRef
  ): Props = Props(new JmsKeepAliveActor(vendor, provider ,interval, maxKeepAlivesMissed, keepAliveActor, reconnectTarget))
}

class JmsKeepAliveActor(
  vendor : String,
  provider : String,
  interval : FiniteDuration,
  maxKeeAlivesMissed : Int,
  keepAliveTarget : ActorRef,
  reconnectTarget : ActorRef
) extends Actor {

  private val log : Logger = Logger[JmsKeepAliveActor]
  private implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private case object Tick

  override def preStart(): Unit = {
    val timer : Cancellable = context.system.scheduler.scheduleOnce(interval, self, Tick)
    context.become(running(timer, 0))
  }

  override def receive: Receive = Actor.emptyBehavior

  private def running(timer : Cancellable, missed: Int) : Receive = {
    case Tick =>
      log.warn(s"KeepAlive[$vendor:$provider] did not receive any messages within [$interval], missed keep alives : [${missed + 1}")
      val timer : Cancellable = context.system.scheduler.scheduleOnce(interval, self, Tick)
      keepAliveTarget ! KeepAlive(vendor, provider)
      context.become(running(timer, missed + 1))
  }
}
