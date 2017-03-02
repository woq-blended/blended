package blended.jms.utils.internal

import javax.jms.{Connection, ConnectionFactory}

import blended.jms.utils.BlendedJMSConnection
import org.slf4j.LoggerFactory

case class ConnectionHolder(provider: String, cf: ConnectionFactory) {

  private[this] val log = LoggerFactory.getLogger(classOf[ConnectionHolder])

  private[this] var conn : Option[BlendedJMSConnection] = None

  def getConnection() : Option[BlendedJMSConnection] = conn

  def connect() : Connection = conn match {
    case Some(c) => c
    case None =>
      log.debug(s"Creating underlying connection for provider [$provider]")
      val c = cf.createConnection()
      c.start()

      conn = Some(new BlendedJMSConnection(c))
      c
  }

  def close() : Unit = {
    log.debug(s"Closing underlying connection for provider [$provider]")
    conn.foreach(_.connection.close())
    conn = None
  }
}
