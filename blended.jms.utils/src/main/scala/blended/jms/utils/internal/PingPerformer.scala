package blended.jms.utils.internal

import java.text.SimpleDateFormat
import java.util.Date
import javax.jms._

import akka.actor.ActorRef
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

abstract class PingPerformer(pingActor: ActorRef, id: String) {

  val provider = id

  val df = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")

  def start() : Unit = {}

  def ping() : Unit

  def close() : Unit = {}
}

class JmsPingPerformer(pingActor: ActorRef, provider: String, con: Connection, destName : String)
  extends PingPerformer(pingActor, provider) with MessageListener {

  private[this] val log = LoggerFactory.getLogger(classOf[JmsPingPerformer])
  private[this] val pingId = df.format(new Date())

  var session : Option[Session] = None

  override def start(): Unit = {
    session = Some(con.createSession(false, Session.AUTO_ACKNOWLEDGE))
  }

  override def onMessage(m: Message): Unit = {
    val text = if (m.isInstanceOf[TextMessage]) m.asInstanceOf[TextMessage].getText() else "UNKNOWN"
    log.trace(s"received ping message [$text] for provider [$provider]")
    pingActor ! PingReceived(text)
  }

  override def ping(): Unit = {
    session match {
      case None => pingActor ! PingResult(Left(new Exception(s"No session established for JMS checker [$provider, $pingId]")))
      case Some(s) =>
        val dest = s.createTopic(destName)
        val consumer = s.createConsumer(dest)
        consumer.setMessageListener(this)

        val producer = s.createProducer(dest)

        try {
          log.debug(s"sending ping message [$pingId] to provider [$provider]")
          producer.send(s.createTextMessage(pingId))
        } finally {
          producer.close()
        }
    }
  }

  override def close(): Unit = {
    session.foreach{ s => {
      try {
        s.close()
        session = None
      } catch {
        case NonFatal(e) => log.warn(s"Error closing session for JMS checker [$provider, $pingId]")
      }
    }}
  }
}
