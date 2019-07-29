package blended.jms.utils.internal

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import blended.jms.utils.{BlendedJMSConnection, BlendedJMSConnectionConfig}
import javax.jms._

import scala.util.Try

class DummyConnection extends Connection {

  var clientId : String = "clientId"
  var el : ExceptionListener = _

  override def createSession(b : Boolean, i : Int) : Session = ???

  override def getClientID() : String = clientId

  override def setClientID(s : String) : Unit = clientId = s

  override def getMetaData : ConnectionMetaData = ???

  override def getExceptionListener : ExceptionListener = el

  override def setExceptionListener(exceptionListener : ExceptionListener) : Unit = el = exceptionListener

  override def start() : Unit = {}

  override def stop() : Unit = {}

  override def close() : Unit = {}

  override def createConnectionConsumer(destination : Destination, s : String, serverSessionPool : ServerSessionPool, i : Int) : ConnectionConsumer = ???

  override def createDurableConnectionConsumer(
    topic : Topic,
    s : String,
    s1 : String,
    serverSessionPool : ServerSessionPool,
    i : Int
  ) : ConnectionConsumer = ???
}

class DummyHolder(f : () => Connection, maxConnects : Int = Int.MaxValue)(implicit system : ActorSystem)
  extends ConnectionHolder(BlendedJMSConnectionConfig.defaultConfig) {

  private val conCount : AtomicInteger = new AtomicInteger(0)

  override val vendor : String = "dummy"
  override val provider : String = "dummy"

  override def getConnectionFactory() : ConnectionFactory = ???

  private[this] var conn : Option[BlendedJMSConnection] = None

  override def getConnection() : Option[BlendedJMSConnection] = conn

  override def connect() : Connection = conn match {
    case Some(c) => c
    case None =>
      if (conCount.get() < maxConnects) {
        conCount.incrementAndGet()
        val c = new BlendedJMSConnection(f())
        conn = Some(c)
        c
      }else {
        throw new Exception("Max connects exceeded")
      }
  }

  override def close() : Try[Unit] = Try {
    conn.foreach { c => c.connection.close() }
    conn = None
  }
}
