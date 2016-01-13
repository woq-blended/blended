package blended.jms.utils.internal

import java.util.concurrent.TimeUnit
import javax.jms.{Connection, ConnectionFactory, Session}

import akka.actor.{Actor, ActorLogging, Cancellable}
import blended.jms.utils.BlendedJMSConnection

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object ConnectionControlActor {

  def apply(provider: String, cf: ConnectionFactory, interval: Int) =
    new ConnectionControlActor(provider, cf, interval)
}

class ConnectionControlActor(provider: String, cf: ConnectionFactory, interval: Int) extends Actor with ActorLogging {

  private[this] implicit val eCtxt = context.system.dispatcher
  private[this] var conn : Option[BlendedJMSConnection] = None

  private[this] val retry = 5.seconds
  private[this] val schedule = Duration(interval, TimeUnit.SECONDS)


  override def preStart(): Unit = {
    log.debug(s"Initialising Connection controller [$provider]")

    context.system.scheduler.scheduleOnce(schedule, self, CheckConnection)
    connect()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.debug(s"Error encountered in ConnectionControlActor [${reason.getMessage}], restarting ...")
    super.preRestart(reason, message)
  }

  override def postStop(): Unit = {
    log.debug(s"Stopping Connection Control Actor for provider [$provider].")
    disconnect()
    super.postStop()
  }

  def checkConnection() : Unit = {

    try {
      val check = Await.result(ping(), 3.seconds)

      check match {
        case Right(_) =>
        case Left(t) => throw t
      }
      context.system.scheduler.scheduleOnce(schedule, self, CheckConnection)
    } catch {
      case e : Throwable =>
        log.debug(s"Error sending connection ping for provider [$provider]. Reconnecting ...")
        disconnect()
        context.system.scheduler.scheduleOnce(5.seconds, self, CheckConnection)
    }
  }

  override def receive : Receive = {
    case GetConnection =>
      sender ! connect()
    case CheckConnection =>
      checkConnection()
  }

  private[this] def connect() : Option[Connection] = {
    conn match {
      case None =>
        try {
          log.debug(s"Creating connection to JMS provider [$provider]")
          val connection = new BlendedJMSConnection(cf.createConnection())
          connection.start()
          conn = Some(connection)
        } catch {
          case e =>
            log.debug(s"Error connecting to JMS provider [$provider].", e)
            conn = None
        }
        conn
      case Some(c) =>
        log.debug(s"Reusing connection for provider [$provider].")
        conn
    }
  }

  private[this] def disconnect() : Unit = {
    conn.foreach { c =>
      log.debug(s"Closing connection for provider [$provider]")

      Future {
        scala.concurrent.blocking { c.connection.close() }
        log.debug(s"Connection closed for provider [$provider]")
      }
    }

    conn = None
  }

  private[this] def ping() : Future[Either[Throwable,Boolean]] = {

    Future {
      var session: Option[Session] = None

      try {
        log.debug(s"Checking connection for provider [$provider]")
        connect() match {
          case None =>
            throw new Exception("No current connection available")
          case Some(c) =>
            session = Some(c.createSession(false, Session.AUTO_ACKNOWLEDGE))
            session.foreach { s =>
              val producer = s.createProducer(s.createTopic("blended.ping"))
              producer.send(s.createTextMessage(s"${System.currentTimeMillis()}"))
              producer.close()
            }
            Right(true)
        }
      } catch {
        case e : Throwable =>
          log.debug(s"Error sending connection ping for provider [$provider]. Reconnecting ...")
          disconnect()
          context.system.scheduler.scheduleOnce(5.seconds, self, CheckConnection)
          Left(e)
      } finally {
        session.foreach(_.close())
      }
    }
  }
}
