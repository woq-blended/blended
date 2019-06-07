package blended.jms.utils.internal

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import javax.jms.Connection

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object JmsConnectionController {
  def props(holder : ConnectionHolder) : Props = Props(new JmsConnectionController(holder))
}

class JmsConnectionController(holder : ConnectionHolder) extends Actor with ActorLogging {

  private[this] implicit val eCtxt : ExecutionContext = context.system.dispatcher

  override def receive : Receive = disconnected

  def disconnected : Receive = LoggingReceive {
    case Connect(t, _) =>
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
      implicit val timeout : Timeout = Timeout(t + 1.second)
      val caller = sender()

      val closer = context.actorOf(ConnectionCloseActor.props(holder))
      closer.ask(Disconnect(t)).onComplete {
        case Success(r) =>
          context.become(disconnected)
          caller ! r
        case Failure(e) =>
          log.warning(s"Unexpected exception closing connection for provider [${holder.provider}] : [${e.getMessage()}]")
      }
  }

  override def toString : String = "JMSConnectionController(" + holder.toString() + ")"
}
