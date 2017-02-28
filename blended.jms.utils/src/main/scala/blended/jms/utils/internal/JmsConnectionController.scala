package blended.jms.utils.internal

import javax.jms.Connection

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object JmsConnectionController {

  def props(holder: ConnectionHolder) = Props(new JmsConnectionController(holder))
}

class JmsConnectionController(holder: ConnectionHolder) extends Actor with ActorLogging {

  private[this] implicit val eCtxt = context.system.dispatcher

  override def receive: Receive = disconnected

  def disconnected : Receive = {
    case Connect(t) =>
      try {
        val c = holder.connect()
        sender ! ConnectResult(t, Right(c))
        context.become(connected(c))
      } catch {
        case NonFatal(e) => sender ! ConnectResult(t, Left(e))
      }
    case Disconnect =>
      sender ! ConnectionClosed
  }

  def connected(c : Connection) : Receive = {
    case Connect(t) =>
      sender ! ConnectResult(t, Right(c))
    case Disconnect(t) =>
      implicit val timeout = Timeout(t)
      val caller = sender()

      val closer = context.actorOf(Props(ConnectionCloseActor(holder)))
      closer.ask(Disconnect(t)).onComplete {
        case Success(r) =>
          context.become(disconnected)
          caller ! r
        case Failure(t) =>
          log.warning(s"Unexpected exception closing connection for provider [${holder.provider}]")
      }
  }
}