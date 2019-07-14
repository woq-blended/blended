package blended.streams.jms

import blended.jms.utils.JmsSession
import javax.jms._

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class JmsConnector(
  id : String,
  jmsSettings : JmsSettings
)(
  onSessionOpened : JmsSession => Try[Unit]
)(
  onSessionClosed : JmsSession => Try[Unit]
)(
  onError : Throwable => Unit
) {

  /**
    * Initialize the connection to be used for this stage. If the connection can't be established,
    * the stage will fail with an exception. For recovery, the stage should be wrapped within a
    * [[blended.streams.StreamControllerSupport]].
    */
  val connection : Connection = Try {
    jmsSettings.log.info(s"Trying to create JMS connection for stream [$id]")
    jmsSettings.connectionFactory.createConnection()
  } match {
    case Success(c) =>
      jmsSettings.log.info(s"Created connection for stream [$id]")
      c.setExceptionListener(new ExceptionListener {
        override def onException(ex : JMSException) : Unit = {
          try {
            c.close() // best effort closing the connection.
          } catch {
            case NonFatal(_) =>
          }
          onError(ex)
        }
      })
      c
    case Failure(e) =>
      jmsSettings.log.error(s"Error creating JMS connection [${e.getMessage()}]")
      onError(e)
      throw e
  }

  val sessionMgr : JmsSessionManager = new JmsSessionManager(
    name = id,
    conn = connection,
    maxSessions = jmsSettings.sessionCount,
    idleTimeout = jmsSettings.sessionIdleTimeout
  ) {
    override def onSessionOpen: JmsSession => Try[Unit] = onSessionOpened
    override def onSessionClose: JmsSession => Try[Unit] = onSessionClosed
    override def onError: Throwable => Unit = onError
  }
}
