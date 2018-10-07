package blended.streams.jms

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.stage.{AsyncCallback, GraphStageLogic, StageLogging}
import blended.jms.utils.{JmsConsumerSession, JmsSession}
import javax.jms._

import scala.concurrent.{ExecutionContext, Future, TimeoutException}

trait JmsConnector[S <: JmsSession] {
  this: GraphStageLogic with StageLogging =>

  implicit protected var ec : ExecutionContext = _
  implicit protected var system : ActorSystem = _

  @volatile protected var jmsConnection: Future[Connection] = _

  protected var jmsSessions = Seq.empty[S]

  protected def jmsSettings: JmsSettings

  protected def onSessionOpened(jmsSession: S): Unit = {}

  protected val fail: AsyncCallback[Throwable] = getAsyncCallback[Throwable](e => failStage(e))

  private val onSession: AsyncCallback[S] = getAsyncCallback[S] { session =>
    jmsSessions :+= session
    onSessionOpened(session)
  }

  protected def createSession(connection: Connection, createDestination: Session => Destination): S

  sealed trait ConnectionStatus
  case object Connecting extends ConnectionStatus
  case object Connected extends ConnectionStatus
  case object TimedOut extends ConnectionStatus

  protected def initSessionAsync(): Unit = {

    def failureHandler(ex: Throwable) = fail.invoke(ex)

    log.debug(s"Creating [${jmsSettings.sessionCount}] session for JMS Source stage")

    val allSessions = openSessions(failureHandler)
    allSessions.failed.foreach(failureHandler)
    // wait for all sessions to successfully initialize before invoking the onSession callback.
    // reduces flakiness (start, consume, then crash) at the cost of increased latency of startup.
    allSessions.foreach(_.foreach(onSession.invoke))
  }

  def openSessions(onConnectionFailure: JMSException => Unit): Future[Seq[S]]

  private def openConnection(startConnection: Boolean)(implicit system: ActorSystem): Future[Connection] = {
    val factory = jmsSettings.connectionFactory
    val connectionRef = new AtomicReference[Option[Connection]](None)

    // status is also the decision point between the two futures below which one will win.
    val status = new AtomicReference[ConnectionStatus](Connecting)

    val connectionFuture = Future {

      val connection = factory.createConnection()

      if (status.get == Connecting) { // `TimedOut` can be set at any point. So we have to check whether to continue.
        connectionRef.set(Some(connection))
        if (startConnection) connection.start()
      }

      // ... and close if the connection is not to be used, don't return the connection
      if (!status.compareAndSet(Connecting, Connected)) {
        connectionRef.get.foreach(_.close())
        connectionRef.set(None)
        throw new TimeoutException("Received timed out signal trying to establish connection")
      } else connection
    }

    val connectTimeout = jmsSettings.connectionTimeout
    val timeoutFuture = after(connectTimeout, system.scheduler) {
      // Even if the timer goes off, the connection may already be good. We use the
      // status field and an atomic compareAndSet to see whether we should indeed time out, or just return
      // the connection. In this case it does not matter which future returns. Both will have the right answer.
      if (status.compareAndSet(Connecting, TimedOut)) {
        connectionRef.get.foreach(_.close())
        connectionRef.set(None)
        Future.failed(
          new TimeoutException(
            s"Timed out after $connectTimeout trying to establish connection. " +
              "Please see ConnectionRetrySettings.connectTimeout"
          )
        )
      } else
        connectionRef.get match {
          case Some(connection) => Future.successful(connection)
          case None => Future.failed(new IllegalStateException("BUG: Connection reference not set when connected"))
        }
    }

    Future.firstCompletedOf(Iterator(connectionFuture, timeoutFuture))
  }

  private[jms] def openConnection(
    startConnection: Boolean,
    onConnectionFailure: JMSException => Unit
  ): Future[Connection] = {

    jmsConnection = openConnection(startConnection).map { connection =>
      connection.setExceptionListener(new ExceptionListener {
        override def onException(ex: JMSException) = {
          try {
            connection.close() // best effort closing the connection.
          } catch {
            case _: Throwable =>
          }
          jmsSessions = Seq.empty

          onConnectionFailure(ex)
        }
      })
      connection
    }(ec)

    jmsConnection
  }
}

trait JmsConsumerConnector[S <: JmsConsumerSession] extends JmsConnector[S] { this: GraphStageLogic with StageLogging =>

  override def openSessions(onConnectionFailure: JMSException => Unit): Future[Seq[S]] = (
    openConnection(startConnection = true, onConnectionFailure).flatMap { connection =>

      val createDestination : Session => Destination = jmsSettings.jmsDestination match {
        case Some(destination) => destination.create
        case _ => throw new IllegalArgumentException("Destination for consumer is missing")
      }

      val sessionFutures =
        for (_ <- 0 until jmsSettings.sessionCount - jmsSessions.size) yield Future(createSession(connection, createDestination))

      Future.sequence(sessionFutures)
    }
  )(ec)
}

//private[jms] trait JmsProducerConnector extends JmsConnector[JmsProducerSession] { this: GraphStageLogic =>
//
//
//
//  protected final def createSession(
//    connection: Connection,
//    createDestination: Session => Destination
//  ): JmsProducerSession = {
//    val session = connection.createSession(false, AcknowledgeMode.AutoAcknowledge.mode)
//    new JmsProducerSession(connection, session, createDestination(session))
//  }
//
//  def openSessions(onConnectionFailure: JMSException => Unit): Future[Seq[JmsProducerSession]] =
//
//    openRecoverableConnection(startConnection = false, onConnectionFailure).flatMap { connection =>
//      val createDestination = jmsSettings.destination match {
//        case Some(destination) => destination.create
//        case _ => throw new IllegalArgumentException("Destination is missing")
//      }
//
//      val sessionFutures =
//        for (_ <- 0 until jmsSettings.sessionCount)
//          yield Future(createSession(connection, createDestination))
//      Future.sequence(sessionFutures)
//    }
//}
