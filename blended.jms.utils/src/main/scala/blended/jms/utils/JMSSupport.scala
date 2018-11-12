package blended.jms.utils

import scala.util.control.NonFatal

import blended.util.logging.Logger
import javax.jms._

@deprecated(message = "The JMSSupport is deprecated, consider using the new API based on Akka streams", since = "2.6.0")
trait JMSSupport {

  private[this] val log = Logger[JMSSupport]

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
        log.debug(e)(s"Encountered JMS Exception [${e.getMessage}]")
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
    msgHandler: JMSMessageHandler,
    errorHandler: JMSErrorHandler,
    maxMessages : Int = 0,
    receiveTimeout : Long = 50,
    subscriptionName : Option[String] = None
  ) : Unit = {

    if (log.isTraceEnabled) {
      val maxMsg = if (maxMessages <= 0) "Unbounded" else s"$maxMessages"
      log.trace(s"Receiving messages from [$destName], maximum count is [$maxMsg], receiveTimeout [$receiveTimeout]")
    }

    withConnection { conn =>
      withSession { session =>

        val d = destination(session, destName)

        val consumer : MessageConsumer = if (d.isInstanceOf[Queue]) {
          log.trace(s"Creating consumer for [$destName]")
          session.createConsumer(d)
        } else {
          subscriptionName match {
            case Some(n) =>
              log.trace(s"Creating durable subscriber for [$destName] with subscription Name [$n]")
              session.createDurableSubscriber(d.asInstanceOf[Topic], n)
            case None => throw new JMSException(s"Subscriber Name undefined for creating durable subscriber for [$destName]")
          }
        }

        var msg : Option[Message] = None
        var msgCount : Int = 0

        do {
          msg = Option(consumer.receive(receiveTimeout))

          msg match {
            case None =>
              log.trace(s"No more messages to receive from [$destName]")
            case Some(m) =>
              msgCount += 1
              val id = m.getJMSMessageID
              log.trace(s"Handling received message [$id] from [$destName]")
              msgHandler.handleMessage(m)  match {
                case Some(t) =>
                  log.warn(s"Error handling message [$id] from [$destName]")
                  if (errorHandler.handleError(m, t)) m.acknowledge()
                case None =>
                  log.trace(s"Successfully handled message [$id] from [$destName]")
                  m.acknowledge()
              }
            }
        } while(msg.isDefined && (maxMessages <=0 || msgCount < maxMessages))

        consumer.close()
      } (con = conn, transacted = false, mode = Session.CLIENT_ACKNOWLEDGE)
    } (cf)
  }

  def sendMessage[T](
    cf: ConnectionFactory,
    destName: String,
    content: T,
    msgFactory: JMSMessageFactory[T],
    deliveryMode: Int = DeliveryMode.NON_PERSISTENT,
    priority : Int = 4,
    ttl : Long = 0
  ) : Option[Throwable] = {

    withConnection { conn =>
      withSession { session =>
        try {
          val producer = session.createProducer(destination(session, destName))
          val msg = msgFactory.createMessage(session, content)
          producer.send(msg, deliveryMode, priority, ttl)
          log.debug(s"Message sent successfully to [$destName]")
          producer.close()
          None
        } catch {
          case NonFatal(t) =>
            log.error(s"Error sending message to [$destName] : [${t.getMessage}]")
            Some(t)
        }
      } (conn)
    } (cf)
  }
}
