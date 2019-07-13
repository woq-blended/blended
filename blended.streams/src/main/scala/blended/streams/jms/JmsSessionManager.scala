package blended.streams.jms

import akka.actor.ActorSystem
import blended.jms.utils.JmsSession
import blended.util.logging.Logger
import javax.jms.{Connection, Session}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Manage JMS sessions on behalf of a JMS streams source or sink.
  *
  * @param conn The JMS connection that will be used to create the sessions.
  * @param maxSessions The maximum number of sessions this manager will create.
  * @param idleTimeout The timeout after which an unused session will be closed.
  */
class JmsSessionManager(
  conn : Connection,
  maxSessions : Int,
  idleTimeout : FiniteDuration = 30.seconds
) {

  private val log : Logger = Logger[JmsSessionManager]

  /**
    * A callback that will be called after a new session has been created.
    */
  def onSessionOpen : JmsSession => Try[Unit] = { _ => Success(()) }

  /**
    * A callback that will be called immediately before a session will be closed.
    */
  def beforeSessionClose : JmsSession => Try[Unit] = { _ => Success(()) }

  /**
    * A callback that will be called after a session has been closed.
    */
  def afterSessionClose : JmsSession => Try[Unit] = { _ => Success(()) }

  // We maintain a map of currently open sessions
  private val sessions : mutable.Map[String, JmsSession] = mutable.Map.empty

  def getSession(id : String): Try[Option[JmsSession]] = {

    sessions.get(id) match {
      case Some(s) =>
        log.debug(s"Reusing existing session for [$id]")
        Success(Some(s))
      case None =>
        if (sessions.size < maxSessions) {
          try {
            log.debug(s"Creating session with id [$id]")
            val s : JmsSession = JmsSession(
              conn.createSession(false, Session.CLIENT_ACKNOWLEDGE), id
            )
            sessions.put(s.sessionId, s)
            onSessionOpen(s)
            Success(Some(s))
          } catch {
            case NonFatal(t) =>
              log.error(t)(s"Failed to create session : [${t.getMessage()}]")
              Failure(t)
          }
        } else {
          log.warn(s"No free session slot available [$maxSessions].")
          Success(None)
        }
    }
  }

  def closeSession(id : String) : Try[Unit] = sessions.remove(id).map { sess =>
    sess.closeSession()
    afterSessionClose(sess)
  }.getOrElse( Success() )

  def closeSessionAsync(id : String)(system : ActorSystem) : Future[Unit] =
    sessions.remove(id).map { _.closeSessionAsync()(system) }.getOrElse( Future {}(system.dispatcher) )

  def closeAll() : Try[Unit] = {
    sessions.values.map{ sess =>
      closeSession(sess.sessionId)
    }.find(_.isFailure).getOrElse(Success())
  }
}
