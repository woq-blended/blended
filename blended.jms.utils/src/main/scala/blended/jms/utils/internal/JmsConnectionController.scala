package blended.jms.utils.internal

import javax.jms.Connection

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object JmsConnectionController {
  def props(holder: ConnectionHolder) = Props(new JmsConnectionController(holder))
}

class JmsConnectionController(holder: ConnectionHolder) extends Actor with ActorLogging {

  private[this] implicit val eCtxt = context.system.dispatcher

  override def receive: Receive = disconnected

  def disconnected : Receive = LoggingReceive {
    case Connect(t, id) =>
      val caller = sender()

      try {
        val c = holder.connect()
        caller ! ConnectResult(t, Right(c))
        context.become(connected(c))
      } catch {
        case NonFatal(e) => caller ! ConnectResult(t, Left(e))
      }
    case Disconnect(_) =>
      sender() ! ConnectionClosed
  }

  def connected(c : Connection) : Receive = LoggingReceive {
    case Connect(t, _) =>
      sender ! ConnectResult(t, Right(c))
    case Disconnect(t) =>
      implicit val timeout = Timeout(t + 1.second)
      val caller = sender()

      val closer = context.actorOf(ConnectionCloseActor.props(holder))
      closer.ask(Disconnect(t)).onComplete {
        case Success(r) =>
          context.become(disconnected)
          caller ! r
        case Failure(t) =>
          log.warning(s"Unexpected exception closing connection for provider [${holder.provider}] : [${t.getMessage()}]")
      }
  }

  override def toString: String = "JMSConnectionController(" + holder.toString() + ")"
}
