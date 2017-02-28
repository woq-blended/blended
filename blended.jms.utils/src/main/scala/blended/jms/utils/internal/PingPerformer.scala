package blended.jms.utils.internal

import java.text.SimpleDateFormat
import java.util.Date
import javax.jms._

import akka.actor.ActorRef
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

abstract class PingPerformer(pingActor : ActorRef) {

  val df = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")

  def start() : Unit = {}

  def ping() : Unit

  def close() : Unit = {}
}

class JmsPingPerformer(pingActor: ActorRef, provider: String, con: Connection, destName : String)
  extends PingPerformer(pingActor) with MessageListener {

  private[this] val log = LoggerFactory.getLogger(classOf[JmsPingPerformer])
  private[this] val pingId = df.format(new Date())

  var session : Option[Session] = None

  override def start(): Unit = {
    try {
      session = Some(con.createSession(false, Session.AUTO_ACKNOWLEDGE))
    } catch {
      case NonFatal(e) => pingActor ! PingResult(Left(e))
    }
  }

  override def onMessage(m: Message): Unit = {
    val text = if (m.isInstanceOf[TextMessage]) m.asInstanceOf[TextMessage].getText() else "UNKNOWN"
    pingActor ! PingReceived(text)
  }

  override def ping(): Unit = {
    session match {
      case None => pingActor ! PingResult(Left(new Exception(s"No session established for JMS checker [$provider, $pingId]")))
      case Some(s) => try {

        val dest = s.createTopic("destName")
        val consumer = s.createConsumer(dest)
        s.setMessageListener(this)

        val producer = s.createProducer(dest)
        producer.send(s.createTextMessage(pingId))
        producer.close()
      } catch {
        case NonFatal(e) =>
          pingActor ! PingResult(Left(e))
      }
    }
  }

  override def close(): Unit = {
    session.foreach{ s => {
      try {
        s.close()
      } catch {
        case NonFatal(e) => log.warn(s"Error closing session for JMS checker [$provider, $pingId]")
      }
    }}
  }
}
