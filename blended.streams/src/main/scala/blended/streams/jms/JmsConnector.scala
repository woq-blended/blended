package blended.streams.jms

import akka.actor.ActorSystem
import akka.stream.KillSwitch
import akka.stream.stage.{AsyncCallback, TimerGraphStageLogic}
import blended.jms.utils.{IdAwareConnectionFactory, JmsSession}
import javax.jms._

import scala.util.{Failure, Success, Try}

trait JmsConnector { this : TimerGraphStageLogic =>

  protected def system : ActorSystem
  protected def jmsSettings : JmsSettings

  protected def onSessionOpened : JmsSession => Try[Unit] = { _ => Success(()) }
  protected def beforeSessionCloseCallback : JmsSession => Try[Unit] = { _ => Success(()) }
  protected def afterSessionCloseCallback : JmsSession => Try[Unit] = { _ => Success(()) }

  // Just to identify the Source stage in log statements
  protected val id : String = {
    val result : String = jmsSettings.connectionFactory match {
      case idAware : IdAwareConnectionFactory => idAware.id
      case cf                                 => cf.toString()
    }

    result
  }

  /**
    * Log an exception and then fail the stage.
    */
  protected val handleError : AsyncCallback[Throwable] = getAsyncCallback[Throwable] { t =>
    jmsSettings.log.error(s"Failing stage [$id] with [${t.getMessage()}")
    failStage(t)
  }

  /**
    * Initialize the connection to be used for this stage. If the connection can't be established,
    * the stage will fail with an exception. For recovery, the stage should be wrapped within a
    * [[blended.streams.StreamControllerSupport]].
    */
  protected val connection : Connection = Try {
    jmsSettings.connectionFactory.createConnection()
  } match {
    case Success(c) =>
      c.setExceptionListener(new ExceptionListener {
        override def onException(ex : JMSException) : Unit = {
          try {
            connection.close() // best effort closing the connection.
          } catch {
            case _ : Throwable =>
          }

          handleError.invoke(ex)
        }
      })
      c
    case Failure(e) =>
      handleError.invoke(e)
      throw e
  }

  protected val sessionMgr : JmsSessionManager = new JmsSessionManager(
    conn = connection,
    maxSessions = jmsSettings.sessionCount,
    idleTimeout = jmsSettings.sessionIdleTimeout
  ) {
    override def onSessionOpen: JmsSession => Try[Unit] = onSessionOpened
    override def beforeSessionClose: JmsSession => Try[Unit] = beforeSessionCloseCallback
    override def afterSessionClose: JmsSession => Try[Unit] = afterSessionCloseCallback
  }

  // We expose the killswitch, so that the stage can be closed externally
  protected def killSwitch = new KillSwitch {
    override def shutdown() : Unit = sessionMgr.closeAll()
    override def abort(ex : Throwable) : Unit = sessionMgr.closeAll()
  }

  override def postStop() : Unit = {
    sessionMgr.closeAll()
  }
}

