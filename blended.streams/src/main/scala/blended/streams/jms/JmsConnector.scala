package blended.streams.jms

import blended.jms.utils.JmsSession
import blended.util.RichTry._
import javax.jms._

import scala.util.{Failure, Success, Try}

class JmsConnector(
  id : String,
  jmsSettings : JmsSettings
)(
  onSessionOpened : JmsSession => Try[Unit]
)(
  onSessionClosed : JmsSession => Try[Unit]
)(
  handleError : Throwable => Unit
) {

  /**
    * Initialize the connection to be used for this stage. If the connection can't be established,
    * the stage will fail with an exception. For recovery, the stage should be wrapped within a
    * [[blended.streams.StreamControllerSupport]].
    */
  private val connection : Option[Connection] = Try {
    jmsSettings.log.debug(s"Trying to create JMS connection for stream [$id]")
    jmsSettings.connectionFactory.createConnection()
  } match {
    case Success(c) =>
      Some(c)
    case Failure(e) =>
      handleError(e)
      None
  }

  private val sessionMgr : Try[JmsSessionManager] = Try {
    connection match {
      case Some(c) =>
        new JmsSessionManager(
          name = id,
          conn = c,
          maxSessions = jmsSettings.sessionCount,
          idleTimeout = jmsSettings.sessionIdleTimeout
        ) {
          override def onSessionOpen: JmsSession => Try[Unit] = onSessionOpened
          override def onSessionClose: JmsSession => Try[Unit] = onSessionClosed
          override def onError: Throwable => Unit = handleError
        }

      case None =>
        throw new IllegalStateException(s"No connection available ... no session manager created")
    }
  }

  private def withSessionMgr[T](f : JmsSessionManager => T) : Try[T] = Try {
    sessionMgr match {
      case Success(mgr) => f(mgr)
      case Failure(t) => throw t
    }
  }

  def getSession(id : String) : Option[JmsSession] = withSessionMgr[Option[JmsSession]]{ mgr =>
    mgr.getSession(id).unwrap
  } match {
    case Success(v) => v
    case Failure(t) =>
      handleError(t)
      None
  }

  def closeSession(id : String) : Unit = withSessionMgr[Unit]{ mgr =>
    mgr.closeSession(id).unwrap
  } match {
    case Success(_) => ()
    case Failure(t) =>
      handleError(t)
      ()
  }

  def closeAll() : Unit = withSessionMgr[Unit]{ mgr =>
    mgr.closeAll()
  } match {
    case Success(_) => ()
    case Failure(t) =>
      handleError(t)
      ()
  }
}
