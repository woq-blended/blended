package blended.jms.utils

import java.util.concurrent.ArrayBlockingQueue

import blended.util.logging.Logger
import javax.jms._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

abstract class JmsSession {

  def connection: Connection

  def session: Session

  def closeSessionAsync()(implicit ec: ExecutionContext): Future[Unit] = Future { closeSession() }

  def closeSession(): Unit = session.close()

  def abortSessionAsync()(implicit ec: ExecutionContext): Future[Unit] = Future { abortSession() }

  def abortSession(): Unit = closeSession()

  def sessionId : String
}

case class JmsProducerSession(
  val connection: Connection,
  val session: Session,
  override val sessionId : String,
  val jmsDestination: Option[JmsDestination]
) extends JmsSession

class JmsConsumerSession(
  val connection: Connection,
  val session: Session,
  override val sessionId : String,
  val jmsDestination: JmsDestination
) extends JmsSession {

  def createConsumer(
    selector: Option[String]
  )(implicit ec: ExecutionContext): Future[MessageConsumer] =
    Future {
      (selector, jmsDestination) match {
        case (None, t: JmsDurableTopic) =>
          session.createDurableSubscriber(t.create(session).asInstanceOf[Topic], t.subscriberName)

        case (Some(expr), t: JmsDurableTopic) =>
          session.createDurableSubscriber(t.create(session).asInstanceOf[Topic], t.subscriberName, expr, false)

        case (Some(expr), q) =>
          session.createConsumer(q.create(session).asInstanceOf[Queue], expr)

        case (None, q) =>
          session.createConsumer(q.create(session).asInstanceOf[Queue])
      }
    }
}

class JmsAckSession(
  override val connection: Connection,
  override val session: Session,
  override val sessionId : String,
  override val jmsDestination: JmsDestination,
  val ackTimeout : FiniteDuration = 1.second
) extends JmsConsumerSession(connection, session, sessionId, jmsDestination) {

  private[this] val log = Logger[JmsAckSession]

  val ackQueue = new ArrayBlockingQueue[Either[Throwable, String]](2)

  def ack(message: Message): Unit = {
    ackQueue.put(Right(sessionId))
  }

}
