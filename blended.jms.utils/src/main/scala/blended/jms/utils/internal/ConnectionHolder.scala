package blended.jms.utils.internal

import javax.jms.{Connection, ConnectionFactory}

import blended.jms.utils.BlendedJMSConnection
import org.slf4j.LoggerFactory

case class ConnectionHolder(
  vendor: String,
  provider: String,
  cf: ConnectionFactory
) {

  private[this] val log = LoggerFactory.getLogger(classOf[ConnectionHolder])

  private[this] var conn : Option[BlendedJMSConnection] = None

  def getConnection() : Option[BlendedJMSConnection] = conn

  def connect(id: String) : Connection = conn match {
    case Some(c) => c
    case None =>
      log.info(s"Creating underlying connection for [$vendor:$provider] with client id [$id]")

      val c = cf.createConnection()
      c.setClientID(id)
      c.start()

      val wrappedConnection = new BlendedJMSConnection(c)
      conn = Some(wrappedConnection)
      wrappedConnection
  }

  def close() : Unit = {
    log.info(s"Closing underlying connection for provider [$provider]")
    conn.foreach(_.connection.close())
    conn = None
  }
}
