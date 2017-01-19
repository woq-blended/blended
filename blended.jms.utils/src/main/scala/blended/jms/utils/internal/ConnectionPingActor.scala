package blended.jms.utils.internal

import javax.jms._

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object ConnectionPingActor {

  def props(controller: ActorRef, con: Connection, destName: String, timeout: FiniteDuration) = Props(
    new ConnectionPingActor(controller, con, destName, timeout)
  )

}

class ConnectionPingActor(controller: ActorRef, con: Connection, destName : String, timeout: FiniteDuration)
  extends Actor with ActorLogging {

  case object Timeout
  case object Cleanup

  case class PingReceived(m : Message)
  case class PerformPing(session: Session)

  implicit val eCtxt = context.system.dispatcher

  var isTimeout = false

  private class PingListener(a : ActorRef) extends MessageListener {

    override def onMessage(message: Message): Unit = a ! PingReceived(message)
  }

  override def preStart(): Unit = {

    super.preStart()

    val session = con.createSession(false, Session.AUTO_ACKNOWLEDGE)

    val consumer = session.createConsumer(session.createTopic(destName))

    consumer.setMessageListener(new PingListener(self))

    self ! PerformPing(session)
  }

  def stop(s: Session) : Receive = {
    case Cleanup =>
      s.close()
      context.stop(self)
  }

  override def receive: Receive = {
    case PerformPing(s) =>
      context.become(pinging(s, context.system.scheduler.scheduleOnce(timeout, self, Timeout)))

      try {
        val producer = s.createProducer(s.createTopic(destName))
        producer.send(s.createTextMessage(s"${System.currentTimeMillis()}"))
        producer.close()

      } catch {
        case NonFatal(e) =>
          controller ! PingResult(Left(e))
          self ! Cleanup
      }
  }

  def pinging(session: Session, timer: Cancellable): Receive = {

    case Timeout =>
      isTimeout = true
      controller ! PingTimeout
      self ! Cleanup

    case PingReceived(m) =>
      if (isTimeout) {
        controller ! PingResult(Right(m))
        timer.cancel()
      }
      self ! Cleanup

    case Cleanup =>
      session.close()
      context.stop(self)
  }
}
