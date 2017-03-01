package blended.jms.utils.internal

import akka.actor._
import akka.pattern.pipe

import scala.concurrent.Future

object ConnectionCloseActor {
  def apply(holder: ConnectionHolder) = new ConnectionCloseActor(holder)
}

/**
  * This Actor will execute and monitor a single close operation on a given JMS connection
  * and then stop itself.
  * @param holder
  */
class ConnectionCloseActor(holder: ConnectionHolder) extends Actor with ActorLogging {

  private[this] implicit val eCtxt = context.system.dispatcher

  override def receive: Receive = {

    case Disconnect(t) =>
      // Just schedule a timeout message in case the Future takes too long
      context.become(waiting(sender(), context.system.scheduler.scheduleOnce(t, self, CloseTimeout)))

      val f = Future {
        holder.close()
        ConnectionClosed
      }

      f.pipeTo(self)
  }

  def waiting(caller: ActorRef, timer: Cancellable) : Receive = {
    case CloseTimeout =>
      timer.cancel()
      caller ! CloseTimeout
      context.stop(self)

    case ConnectionClosed =>
      timer.cancel()
      caller ! ConnectionClosed
      context.stop(self)
  }
}
