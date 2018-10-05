package blended.jms.utils

import java.util.concurrent.ArrayBlockingQueue

import javax.jms._

import scala.concurrent.{ExecutionContext, Future}

sealed trait JmsSession {

  def connection: Connection

  def session: Session

  def closeSessionAsync()(implicit ec: ExecutionContext): Future[Unit] = Future { closeSession() }

  def closeSession(): Unit = session.close()

  def abortSessionAsync()(implicit ec: ExecutionContext): Future[Unit] = Future { abortSession() }

  def abortSession(): Unit = closeSession()
}

class JmsProducerSession(
  val connection: Connection,
  val session: Session,
  val jmsDestination: JmsDestination
) extends JmsSession

class JmsConsumerSession(
  val connection: Connection,
  val session: Session,
  val jmsDestination: JmsDestination,
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
  override val jmsDestination: JmsDestination,
  val maxPendingAcks: Int
) extends JmsConsumerSession(connection, session, jmsDestination) {

  var pendingAck = 0
  val ackQueue = new ArrayBlockingQueue[() => Unit](maxPendingAcks + 1)

  def ack(message: Message): Unit = ackQueue.put(() => message.acknowledge())

  override def closeSession(): Unit = stopMessageListenerAndCloseSession()

  override def abortSession(): Unit = stopMessageListenerAndCloseSession()

  private def stopMessageListenerAndCloseSession(): Unit = {
    ackQueue.put(() => throw StopMessageListenerException())
    session.close()
  }
}

final case class StopMessageListenerException() extends Exception("Stopping MessageListener.")
