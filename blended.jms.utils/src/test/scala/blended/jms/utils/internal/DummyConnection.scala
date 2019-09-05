package blended.jms.utils.internal

import akka.actor.ActorSystem
import blended.jms.utils.{BlendedJMSConnection, BlendedJMSConnectionConfig}
import javax.jms._

import scala.util.Try

class DummyConnection extends Connection {

  var clientId : String = "clientId"
  var el : ExceptionListener = _

  override def createSession(b: Boolean, i: Int): Session = ???

  override def getClientID() : String = clientId

  override def setClientID(s: String): Unit = clientId = s

  override def getMetaData: ConnectionMetaData = ???

  override def getExceptionListener: ExceptionListener = el

  override def setExceptionListener(exceptionListener: ExceptionListener): Unit = el = exceptionListener

  override def start(): Unit = {}

  override def stop(): Unit = {}

  override def close(): Unit = {}

  override def createConnectionConsumer(destination: Destination, s: String, serverSessionPool: ServerSessionPool, i: Int): ConnectionConsumer = ???

  override def createDurableConnectionConsumer(topic: Topic, s: String, s1: String, serverSessionPool: ServerSessionPool, i: Int): ConnectionConsumer = ???
}

class DummyHolder(f : () => Connection)(implicit system: ActorSystem)
  extends ConnectionHolder(BlendedJMSConnectionConfig.defaultConfig) {

  override val vendor: String = "dummy"
  override val provider: String = "dummy"

  override def getConnectionFactory(): ConnectionFactory = new ConnectionFactory {
    override def createConnection(): Connection = f()
    override def createConnection(userName: String, password: String): Connection = createConnection()
  }
}
