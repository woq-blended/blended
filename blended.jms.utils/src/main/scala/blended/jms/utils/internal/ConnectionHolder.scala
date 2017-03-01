package blended.jms.utils.internal

import javax.jms.{Connection, ConnectionFactory}

case class ConnectionHolder(provider: String, cf: ConnectionFactory) {

  private[this] var conn : Option[Connection] = None

  def getConnection() : Option[Connection] = conn

  def connect() : Connection = conn match {
    case Some(c) => c
    case None =>
      val c = cf.createConnection()
      c.start()

      conn = Some(c)
      c
  }

  def close() : Unit = {
    conn.foreach(_.close())
    conn = None
  }
}
