package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.event.LoggingReceive

import scala.concurrent.duration._
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

  implicit val eCtxt = context.system.dispatcher

  var isTimeout = false
  var hasPinged = false

  override def receive: Receive = LoggingReceive {
    case p : Props =>

      val pingPerformer = context.actorOf(p)
      val timer = context.system.scheduler.scheduleOnce(timeout, self, Timeout)
      val caller = sender()
      context.become(pinging(caller, timer))
      pingPerformer ! ExecutePing(self)
  }

  def pinging(caller : ActorRef, timer: Cancellable): Receive = LoggingReceive {

    case Timeout =>
      if (!hasPinged) {
        isTimeout = true
        caller ! PingTimeout
      }
      context.stop(self)

    case r : PingResult =>
      timer.cancel()
      caller ! r
      context.stop(self)

    case PingReceived(m) =>
      if (!isTimeout) {
        timer.cancel()
        caller ! PingResult(Right(m))
        hasPinged = true
      }
      context.stop(self)

  }
}
