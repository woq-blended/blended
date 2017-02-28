package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object ConnectionPingActor {

  def props(controller: ActorRef, timeout: FiniteDuration) = Props(
    new ConnectionPingActor(controller, timeout)
  )
}

class ConnectionPingActor(controller: ActorRef, timeout: FiniteDuration)
  extends Actor with ActorLogging {

  case object Timeout
  case object Cleanup

  implicit val eCtxt = context.system.dispatcher

  var isTimeout = false
  var hasPinged = false

  override def receive: Receive = {
    case p : PingPerformer =>
      try {
        p.start()
        p.ping()
      } catch {
        case NonFatal(e) => controller ! PingResult(Left(e))
      }
      context.become(pinging(p, context.system.scheduler.scheduleOnce(timeout, self, Timeout)))
  }

  def pinging(performer: PingPerformer, timer: Cancellable): Receive = {

    case Timeout =>
      if (!hasPinged) {
        isTimeout = true
        controller ! PingTimeout
      }
      self ! Cleanup

    case PingResult(r) =>
      timer.cancel()
      controller ! r
      self ! Cleanup

    case PingReceived(m) =>
      if (!isTimeout) {
        timer.cancel()
        controller ! PingResult(Right(m))
        hasPinged = true
      }
      self ! Cleanup

    case Cleanup =>
      performer.close()
      context.stop(self)
  }
}
