package blended.jms.utils.internal

import akka.actor._
import akka.pattern.pipe

import scala.concurrent.Future

object ConnectionCloseActor {
  def apply(holder: ConnectionHolder) = new ConnectionCloseActor(holder)
}

class ConnectionCloseActor(holder: ConnectionHolder) extends Actor with ActorLogging {

  private[this] implicit val eCtxt = context.system.dispatcher
  private[this] var timer : Option[Cancellable] = None

  override def receive: Receive = {

    case Disconnect(t) =>
      context.become(waiting(sender()))

      val f = Future {
        holder.close()
        ConnectionClosed
      }
      timer = Some(context.system.scheduler.scheduleOnce(t, self, CloseTimeout))
      f.pipeTo(self)
  }

  def waiting(caller: ActorRef) : Receive = {
    case CloseTimeout =>
      timer.foreach(_.cancel())
      caller ! CloseTimeout
      context.stop(self)

    case ConnectionClosed =>
      timer.foreach(_.cancel())
      caller ! ConnectionClosed
      context.stop(self)
  }
}
