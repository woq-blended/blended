package blended.jms.utils.internal

import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.{Connection, ConnectionFactory, ExceptionListener, JMSException}

import akka.actor.ActorSystem
import blended.jms.utils.BlendedJMSConnection
import org.slf4j.LoggerFactory

case class ConnectionHolder(
  provider: String,
  cf: ConnectionFactory,
  system: ActorSystem
) {

  private[this] val log = LoggerFactory.getLogger(classOf[ConnectionHolder])
  private[this] var conn : Option[BlendedJMSConnection] = None

  private[this] var connecting : AtomicBoolean = new AtomicBoolean(false)

  def getConnection() : Option[BlendedJMSConnection] = conn

  def connect(id: String) : Connection = conn match {
    case Some(c) => c
    case None =>
      if (!connecting.getAndSet(true)) {

        try {
          log.info(s"Creating underlying connection for provider [$provider] with client id [$id]")

          val c = cf.createConnection()
          c.setClientID(id)

          c.setExceptionListener(new ExceptionListener {
            override def onException(e: JMSException): Unit = {
              log.warn(s"Exception encountered in connection for provider [$provider] : ${e.getMessage()}")
              system.eventStream.publish(ConnectionException(provider, e))
            }
          })

          c.start()

          val wrappedConnection = new BlendedJMSConnection(c)
          conn = Some(wrappedConnection)

          wrappedConnection
        } catch {
          case e : JMSException => throw e
        } finally {
          connecting.set(false)
        }

      } else {
        throw new JMSException(s"Connection Factory for provider [$provider] is still connecting.")
      }
  }

  def close() : Unit = {
    log.info(s"Closing underlying connection for provider [$provider]")
    conn.foreach(_.connection.close())
    conn = None
  }
}
