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
  extends PingPerformer(pingActor, provider) {

  private[this] val log = LoggerFactory.getLogger(classOf[JmsPingPerformer])
  private[this] val pingId = df.format(new Date())

  var session : Option[Session] = None

  override def start(): Unit = {
    session = Some(con.createSession(false, Session.AUTO_ACKNOWLEDGE))
  }

  override def ping(): Unit = {
    session match {
      case None => pingActor ! PingResult(Left(new Exception(s"No session established for JMS checker [$provider, $pingId]")))
      case Some(s) =>
        val dest = s.createTopic(destName)
        val consumer = s.createConsumer(dest)
        val producer = s.createProducer(dest)

        try {
          log.debug(s"sending ping message [$pingId] to topic [$provider:$destName]")
          producer.send(s.createTextMessage(pingId))

          Option(consumer.receive(100l)) match {
            case None =>
            case Some(msg) =>
              val text = if (msg.isInstanceOf[TextMessage]) msg.asInstanceOf[TextMessage].getText() else "UNKNOWN"
              log.debug(s"received ping message [$text] for provider [$provider]")
              pingActor ! PingReceived(text)
          }
        } finally {
          consumer.close()
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
