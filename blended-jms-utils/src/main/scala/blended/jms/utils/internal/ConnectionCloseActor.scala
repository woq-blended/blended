package blended.jms.utils.internal

import javax.jms.Connection

import akka.actor._
import akka.pattern.pipe

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ConnectionCloseActor {
  def apply(conn: Connection, t: FiniteDuration, controller: ActorRef) = new ConnectionCloseActor(conn, t, controller)
}

class ConnectionCloseActor(conn: Connection, t: FiniteDuration, controller: ActorRef) extends Actor with ActorLogging {

  case object CloseConnection

  implicit val eCtxt = context.system.dispatcher
  private[this] var timer : Option[Cancellable] = None

  override def preStart(): Unit = {
    super.preStart()
    self ! CloseConnection
  }

  override def receive: Receive = {

    case CloseConnection =>
      val f = Future {
        conn.close()
        ConnectionClosed
      }
      timer = Some(context.system.scheduler.scheduleOnce(t, self, CloseTimeout))
      f.pipeTo(self)

    case CloseTimeout =>
      timer.foreach(_.cancel())
      controller ! CloseTimeout
      context.stop(self)

    case ConnectionClosed =>
      timer.foreach(_.cancel())
      controller ! ConnectionClosed
      context.stop(self)
  }
}
