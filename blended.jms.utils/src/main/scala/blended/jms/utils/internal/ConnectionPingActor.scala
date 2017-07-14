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
  case object Cleanup

  implicit val eCtxt = context.system.dispatcher

  var isTimeout = false
  var hasPinged = false

  override def receive: Receive = LoggingReceive {
    case p : PingPerformer =>
      val caller = sender()
      try {
        p.start()
        p.ping()
        context.become(pinging(caller, p, context.system.scheduler.scheduleOnce(timeout, self, Timeout)))
      } catch {
        case NonFatal(e) =>
          log.debug(s"Ping for provider [${p.provider}] failed : [${e.getMessage()}]")
          p.close()
          caller ! PingResult(Left(e))
          context.stop(self)
      }
  }

  def pinging(caller : ActorRef, performer: PingPerformer, timer: Cancellable): Receive = LoggingReceive {

    case Timeout =>
      if (!hasPinged) {
        isTimeout = true
        caller ! PingTimeout
      }
      self ! Cleanup

    case r : PingResult =>
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
