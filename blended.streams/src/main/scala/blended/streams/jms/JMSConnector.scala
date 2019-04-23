package blended.streams.jms

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.stage.{AsyncCallback, TimerGraphStageLogic}
import blended.jms.utils.{IdAwareConnectionFactory, JmsSession}
import blended.util.logging.Logger
import javax.jms._

import scala.concurrent.{ExecutionContext, Future, TimeoutException}

object JmsConnector {

  private[this] val sessionIdCounter : AtomicLong = new AtomicLong(0L)

  def nextSessionId : String = {

    if (sessionIdCounter.get() == Long.MaxValue) {
      sessionIdCounter.set(0l)
    }

    s"${sessionIdCounter.incrementAndGet()}"
  }
}

trait JmsConnector[S <: JmsSession] { this: TimerGraphStageLogic =>

  private case object RecreateSessions

  implicit protected var ec : ExecutionContext = _
  implicit protected var system : ActorSystem = _

  @volatile protected var jmsConnection: Future[Connection] = _

  protected var jmsSessions : Map[String, S] = Map.empty

  protected def jmsSettings: JmsSettings

  protected def onSessionOpened(jmsSession: S): Unit

  // Just to identify the Source stage in log statements
  protected val id : String = { jmsSettings.connectionFactory match {
    case idAware: IdAwareConnectionFactory => idAware.id
    case cf => cf.toString()
  }}

  protected val fail: AsyncCallback[Throwable] = getAsyncCallback[Throwable]{e =>
    jmsSettings.log.warn(s"Failing stage [$id]")
    failStage(e)
  }

  private val onSession: AsyncCallback[S] = getAsyncCallback[S] { session =>
    jmsSettings.log.debug(s"Session of type [${session.getClass().getSimpleName()}] with id [${session.sessionId}] has been created.")
    jmsSessions += (session.sessionId -> session)
    onSessionOpened(session)
  }

  private val onSessionClosed : AsyncCallback[S] = getAsyncCallback { s =>
    if (isTimerActive(RecreateSessions)) {
      // do nothing as we have already scheduled to recreate the sessions
    } else {
      scheduleOnce(RecreateSessions, jmsSettings.sessionRecreateTimeout)
    }

    afterSessionClose(s)
  }

  protected def handleTimer : PartialFunction[Any, Unit] = {
    case RecreateSessions =>
      initSessionAsync()
      cancelTimer(RecreateSessions)
  }

  override protected def onTimer(timerKey: Any): Unit = handleTimer(timerKey)

  protected def nextSessionId() : String = s"$id-${JmsConnector.nextSessionId}"

  protected def createSession(connection: Connection): S

  protected def afterSessionClose(session : S) : Unit = {}

  protected[this] def closeSession(session: S) : Unit = {

    try {
      jmsSettings.log.debug(s"Closing session [${session.sessionId}]")
      session.closeSessionAsync().onComplete { _ =>
        jmsSessions -= session.sessionId
        onSessionClosed.invoke(session)
      }
    } catch {
      case _ : Throwable =>
        jmsSettings.log.error(s"Error closing session with id [${session.sessionId}]")
    }
  }

  sealed trait ConnectionStatus
  case object Connecting extends ConnectionStatus
  case object Connected extends ConnectionStatus
  case object TimedOut extends ConnectionStatus

  protected def initSessionAsync(): Unit = {

    def failureHandler(ex: Throwable) = {
      jmsSettings.log.warn(s"Session creation failed [${ex.getMessage()}]")
      fail.invoke(ex)
    }

    val allSessions = openSessions(failureHandler)

    allSessions.failed.foreach(failureHandler)
    // wait for all sessions to successfully initialize before invoking the onSession callback.
    // reduces flakiness (start, consume, then crash) at the cost of increased latency of startup.
    allSessions.foreach(_.foreach{ s =>
      onSession.invoke(s)
    })(ec)
  }

  def openSessions(onConnectionFailure: JMSException => Unit): Future[Seq[S]] =

    openConnection(startConnection = true, onConnectionFailure).flatMap { connection =>

      val toBeCreated = jmsSettings.sessionCount - jmsSessions.size
      jmsSettings.log.debug(s"Trying to create [$toBeCreated] sessions ...")
      val sessionFutures =
        for (_ <- 0 until toBeCreated) yield Future {
          val s = createSession(connection)
          s
        }

    Future.sequence(sessionFutures)
  }

  private def openConnection(startConnection: Boolean) : Future[Connection] = {

    val factory = jmsSettings.connectionFactory
    val connectionRef = new AtomicReference[Option[Connection]](None)

    // status is also the decision point between the two futures below which one will win.
    val status = new AtomicReference[ConnectionStatus](Connecting)

    val connectionFuture = Future {

      val connection = factory.createConnection()

      if (status.get == Connecting) { // `TimedOut` can be set at any point. So we have to check whether to continue.
        connectionRef.set(Some(connection))
        if (startConnection) {
          connection.start()
        }
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
            s"Timed out after [$connectTimeout] trying to establish connection. " +
              "Please see ConnectionRetrySettings.connectTimeout"
          )
        )
      } else
        connectionRef.get match {
          case Some(connection) =>
            Future.successful(connection)
          case None =>
            Future.failed(new IllegalStateException("BUG: Connection reference not set when connected"))
        }
    }

    Future.firstCompletedOf(Iterator(connectionFuture, timeoutFuture))(ec)
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
          jmsSessions = Map.empty

          onConnectionFailure(ex)
        }
      })
      connection
    }

    jmsConnection
  }
}

