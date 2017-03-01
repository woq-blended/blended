package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object ConnectionPingActor {

  def props(timeout: FiniteDuration) = Props(
    new ConnectionPingActor(timeout)
  )
}

/**
  * This Actor will execute and monitor a single ping operation to check the health
  * of the underlying JMS connection
  * @param timeout
  */
class ConnectionPingActor(timeout: FiniteDuration)
  extends Actor with ActorLogging {

  case object Timeout
  case object Cleanup

  implicit val eCtxt = context.system.dispatcher

  var isTimeout = false
  var hasPinged = false

  override def receive: Receive = {
    case p : PingPerformer =>
      val caller = sender()
      try {
        p.start()
        p.ping()
      } catch {
        case NonFatal(e) => caller ! PingResult(Left(e))
      }
      context.become(pinging(caller, p, context.system.scheduler.scheduleOnce(timeout, self, Timeout)))
  }

  def pinging(caller : ActorRef, performer: PingPerformer, timer: Cancellable): Receive = {

    case Timeout =>
      if (!hasPinged) {
        isTimeout = true
        caller ! PingTimeout
      }
      self ! Cleanup

    case PingResult(r) =>
      timer.cancel()
      caller ! r
      self ! Cleanup

    case PingReceived(m) =>
      if (!isTimeout) {
        timer.cancel()
        caller ! PingResult(Right(m))
        hasPinged = true
      }
      self ! Cleanup

    case Cleanup =>
      performer.close()
      context.stop(self)
  }
}
