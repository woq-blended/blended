package blended.jms.utils

import javax.jms._

import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

trait JMSSupport {

  private[this] val log = LoggerFactory.getLogger(classOf[JMSSupport])

  val TOPICTAG = "topic:"
  val QUEUETAG = "queue:"

  def withSession(f: (Session => Unit))(con: Connection, transacted: Boolean = false, mode: Int = Session.AUTO_ACKNOWLEDGE) : Option[Throwable] = {

    var session : Option[Session] = None

    try {
      session = Some(con.createSession(transacted, mode))
      f(session.get)
      None
    } catch {
      case NonFatal(e) =>
        log.debug(s"Encountered JMS Exception [${e.getMessage}]", e)
        Some(e)
    } finally {
      session.foreach(_.close())
    }
  }

  def withConnection(f: (Connection => Unit))(cf: ConnectionFactory) : Option[Throwable] = {

    var connection : Option[Connection] = None

    try {
      connection = Some(cf.createConnection())
      connection.foreach { c =>
        c.start()
        f(c)
      }
      None
    } catch {
      case NonFatal(e) =>
        Some(e)
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

  def receiveMessage(
    cf : ConnectionFactory,
    destName: String,
    msgHandler: JMSMessageHandler
  ) : Unit = {

    withConnection { conn =>
      withSession { session =>

        val consumer = session.createConsumer(destination(session, destName))

        var msg : Option[Message] = None

        do {
          log.debug(s"Receiving message from [$destName]")
          msg = Option(consumer.receive(10))
          msg.foreach { m =>
            val id = m.getJMSMessageID()
            log.debug(s"Handling received message [$id] from [$destName]")
            msgHandler.handleMessage(m) match {
              case Some(t) =>
                log.warn(s"Error handling message [$id] from [$destName]")
                throw t
              case None =>
                log.debug(s"Successfully handled message [$id] from [$destName]")
                m.acknowledge()
            }
          }
        } while(msg.isDefined)

        log.debug(s"No more messages to process from [$destName] - Idling consumer")
        consumer.close()

      } (con = conn, transacted = false, mode = Session.CLIENT_ACKNOWLEDGE)
    } (cf)
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
        log.debug(s"Sending JMS message to [$destName]")
        val producer = session.createProducer(destination(session, destName))
        val msg = msgFactory.createMessage(session, content)
        log.debug("JMS message created")
        producer.send(msgFactory.createMessage(session, content), deliveryMode, priority, ttl)
        log.debug("Message sent successfully")
        producer.close()
      } (conn)
    } (cf)
  }
}
