package blended.jms.utils.internal

import java.util.concurrent.TimeUnit
import javax.jms.{Session, Connection, ConnectionFactory}

import akka.actor.{Cancellable, ActorLogging, Actor}
import blended.jms.utils.BlendedJMSConnection
import org.apache.camel.CamelContext
import org.apache.camel.component.jms.JmsComponent
import org.apache.camel.impl.DefaultCamelContext

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
  }

  override def postStop(): Unit = {
    conn.foreach { c =>
      log.info(s"Closing connection for provider [$provider]")
      c.connection.close()
    }

    conn = None


    timer.foreach(_.cancel())
    timer = None

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
      val c = conn match {
        case None =>
          log.debug(s"Creating connection to JMS provider [$provider]")
          val connection = new BlendedJMSConnection(cf.createConnection())
          connection.start()
          conn = Some(connection)
          connection
        case Some(connection) =>
          log.debug(s"Reusing connection for provider [$provider].")
          connection
      }

      sender ! c
    case CheckConnection =>
      checkConnection
  }
}
