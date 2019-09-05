package blended.jms.utils.internal

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import blended.util.logging.Logger
import javax.jms.Connection

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

object JmsConnectionController {
  def props(
    holder: ConnectionHolder,
    closer : Props
  ) = Props(new JmsConnectionController(holder, closer))
}

class JmsConnectionController(holder: ConnectionHolder, closer : Props) extends Actor {

  private val log : Logger = Logger(s"${getClass().getName()}.${holder.vendor}.${holder.provider}")
  private implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private case object Tick

  override def receive: Receive = disconnected

  private def disconnected : Receive = {
    case Connect(t, _) =>
      val caller = sender()

      try {
        val c = holder.connect()
        log.debug(s"Successfully connected to [${holder.vendor}:${holder.provider}]")
        caller ! ConnectResult(t, Right(c))
        context.become(connected(c))
      } catch {
        case NonFatal(e) => caller ! ConnectResult(t, Left(e))
      }
    case Disconnect(_) =>
      sender() ! ConnectionClosed
  }

  private def connected(c : Connection) : Receive = {
    case Connect(t, _) =>
      sender ! ConnectResult(t, Right(c))
    case Disconnect(t) =>
      val timer : Cancellable = context.system.scheduler.scheduleOnce(t + 1.second, self, Tick)

      val closeActor = context.actorOf(closer)
      closeActor ! Disconnect(t)

      context.become(disconnecting(sender(), timer))
  }

  private def disconnecting(caller : ActorRef, timer : Cancellable) : Receive = {
    case Tick =>
      log.debug(s"Disconnect for [${holder.vendor}:${holder.provider}] timed out.")
      caller ! CloseTimeout

    case Connect(t, _) =>
      val msg : String = s"ConnectionHolder [${holder.vendor}:${holder.provider}] is currently disconnecting"
      log.debug(msg)
      caller ! ConnectResult(t, Left(new Exception(msg)))

    case CloseTimeout =>
      timer.cancel()
      caller ! CloseTimeout
      context.become(disconnected)

    case ConnectionClosed =>
      timer.cancel()
      caller ! ConnectionClosed
      context.become(disconnected)
  }

  override def toString: String = "JMSConnectionController(" + holder.toString() + ")"
}
