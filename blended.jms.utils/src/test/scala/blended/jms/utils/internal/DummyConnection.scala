package blended.jms.utils.internal

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import blended.jms.utils.ConnectionConfig
import javax.jms._

class DummyConnection(cfg : ConnectionConfig) extends Connection {

  var clientId : String = cfg.provider
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

class DummyHolder(
  cfg : ConnectionConfig,
  f : ConnectionConfig => Connection = c => new DummyConnection(c),
  maxConnects : Int = Int.MaxValue
)(implicit system : ActorSystem)
  extends ConnectionHolder(cfg) {

  private val conCount : AtomicInteger = new AtomicInteger(0)

  override def getConnectionFactory(): ConnectionFactory = new ConnectionFactory {
    override def createConnection(): Connection = {
      if (conCount.get() < maxConnects) {
        conCount.incrementAndGet()
        f(cfg)
      } else {
        throw new Exception("Max connects exceeded")
      }
    }
    override def createConnection(userName: String, password: String): Connection = createConnection()
  }
}
