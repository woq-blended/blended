package blended.jms.utils

import javax.jms._

import scala.util.control.NonFatal

trait JMSSupport {

  val TOPICTAG = "topic:"
  val QUEUETAG = "queue:"

  def withSession(f: (Session => Unit))(con: Connection, transacted: Boolean = false, mode: Int = Session.AUTO_ACKNOWLEDGE) : Option[Throwable] = {

    var session : Option[Session] = None

    try {
      session = Some(con.createSession(transacted, mode))
      f(session.get)
      None
    } catch {
      case NonFatal(e) => Some(e)
    } finally {
      session.foreach(_.close())
    }
  }

  def withConnection(f: (Connection => Unit))(cf: ConnectionFactory) : Option[Throwable] = {

    var connection : Option[Connection] = None

    try {
      connection = Some(cf.createConnection())
      f(connection.get)
      None
    } catch {
      case NonFatal(e) => Some(e)
    } finally {
      connection.foreach(_.close())
    }
  }

  def destination(session: Session, destName: String) : Destination = {
    if (destName.startsWith(TOPICTAG))
      session.createTopic(destName.substring(TOPICTAG.length))
    else if (destName.startsWith(QUEUETAG))
      session.createQueue(destName.substring(QUEUETAG.length))
    else
      session.createQueue(destName)
  }

  def sendMessage(
    cf: ConnectionFactory,
    destName: String,
    content: Option[Any],
    msgFactory: JMSMessageFactory,
    deliveryMode: Int = DeliveryMode.NON_PERSISTENT,
    priority : Int = 4,
    ttl : Long = 0
  ) : Option[Throwable] = {

    withConnection { conn =>
      withSession { session =>
        val producer = session.createProducer(destination(session, destName))
        producer.send(msgFactory.createMessage(session, content), deliveryMode, priority, ttl)
      } (conn)
    } (cf)
  }
}
