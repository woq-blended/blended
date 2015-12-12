package blended.jms.utils.internal

import java.util.concurrent.TimeUnit
import javax.jms.{Connection, ConnectionFactory, Session}

import akka.actor.{Actor, ActorLogging, Cancellable}
import blended.jms.utils.BlendedJMSConnection

import scala.concurrent.duration.Duration

object ConnectionControlActor {

  def apply(provider: String, cf: ConnectionFactory, interval: Int) =
    new ConnectionControlActor(provider, cf, interval)
}

class ConnectionControlActor(provider: String, cf: ConnectionFactory, interval: Int) extends Actor with ActorLogging {

  private[this] implicit val eCtxt = context.system.dispatcher
  private[this] var conn : Option[BlendedJMSConnection] = None
  private[this] var timer : Option[Cancellable] = None

  override def preStart(): Unit = {
    log.debug(s"Initialising Connection controller [$provider]")
    val schedule = Duration(interval, TimeUnit.SECONDS)
    timer = Some(context.system.scheduler.schedule(schedule, schedule, self, CheckConnection))

    connect()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    disconnect()

    timer.foreach(_.cancel())
    timer = None

    super.preRestart(reason, message)
  }

  override def postStop(): Unit = {
    disconnect()
    super.postStop()
  }

  def checkConnection : Unit = conn.foreach { c =>

    var session : Option[Session] = None

    try {
      log.debug(s"Checking connection for provider [$provider]")
      session = Some(c.createSession(false, Session.AUTO_ACKNOWLEDGE))
      session.foreach { s =>
        val producer = s.createProducer(s.createTopic("blended.ping"))
        producer.send(s.createTextMessage(s"${System.currentTimeMillis()}"))
      }
    } finally {
      session.foreach(_.close())
    }
  }

  override def receive : Receive = {
    case GetConnection =>
      sender ! connect()
    case CheckConnection =>
      checkConnection
  }

  private[this] def connect() : Connection = {
    conn match {
      case None =>
        log.debug(s"Creating connection to JMS provider [$provider]")
        val connection = new BlendedJMSConnection(cf.createConnection())
        connection.start()
        conn = Some(connection)
        connection
      case Some(conn) =>
        log.debug(s"Reusing connection for provider [$provider].")
        conn
    }
  }

  private[this] def disconnect() : Unit = {
    conn.foreach { c =>
      log.info(s"Closing connection for provider [$provider]")
      c.connection.close()
    }

    conn = None
  }
}
