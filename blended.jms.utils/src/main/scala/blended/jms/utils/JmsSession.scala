package blended.jms.utils

import akka.actor.ActorSystem
import javax.jms._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

abstract class JmsSession {

  def connection: Connection

  def session: Session

  def closeSessionAsync()(system: ActorSystem): Future[Unit] = {

    implicit val eCtxt : ExecutionContext = system.dispatcher

    val p : Promise[Unit] = Promise()

    Future { closeSession() }.onComplete[Unit] {
      case Success(_) => p.success()
      case Failure(t) => p.failure(t)
    }

    akka.pattern.after(1.seconds, system.scheduler){ Future {
      if (!p.isCompleted) {
        p.failure(new Exception(s"Session close for [$sessionId] has timed out"))
      }
    }}

    p.future
  }

  def closeSession(): Unit = {
    session.close()
  }

  def abortSessionAsync()(implicit ec: ExecutionContext): Future[Unit] = Future { abortSession() }

  def abortSession(): Unit = closeSession()

  def sessionId : String
}

case class JmsProducerSession(
  connection: Connection,
  session: Session,
  override val sessionId : String,
  jmsDestination: Option[JmsDestination]
) extends JmsSession

class JmsConsumerSession(
  val connection: Connection,
  val session: Session,
  override val sessionId : String,
  val jmsDestination: JmsDestination
) extends JmsSession {

  def createConsumer(
    selector: Option[String]
  )(implicit ec: ExecutionContext): Try[MessageConsumer] = Try {
    (selector, jmsDestination) match {
      case (None, t: JmsDurableTopic) =>
        session.createDurableSubscriber(t.create(session).asInstanceOf[Topic], t.subscriberName)

      case (Some(expr), t: JmsDurableTopic) =>
        session.createDurableSubscriber(t.create(session).asInstanceOf[Topic], t.subscriberName, expr, false)

      case (None, t: JmsTopic) =>
        session.createConsumer(t.create(session))

      case (Some(expr), t: JmsTopic) =>
        session.createConsumer(t.create(session), expr, false)

      case (Some(expr), q) =>
        session.createConsumer(q.create(session).asInstanceOf[Queue], expr)

      case (None, q) =>
        session.createConsumer(q.create(session).asInstanceOf[Queue])
    }
  }
}

object JmsAckState extends Enumeration {
  type JmsAckState = Value
  val Pending, Acknowledged, Denied = Value
}

class JmsAckSession(
  override val connection: Connection,
  override val session: Session,
  override val sessionId : String,
  override val jmsDestination: JmsDestination,
  val ackTimeout : FiniteDuration = 1.second
) extends JmsConsumerSession(connection, session, sessionId, jmsDestination) {

  var ackState : JmsAckState.JmsAckState  = JmsAckState.Pending

  def resetAck() : Unit = synchronized {
    ackState = JmsAckState.Pending
  }

  def deny(message : Message) : Unit = synchronized {
    ackState = JmsAckState.Denied

  }

  def ack(message: Message): Unit = synchronized {
    ackState = JmsAckState.Acknowledged
  }
}
