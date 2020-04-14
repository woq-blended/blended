package blended.jms.utils

import javax.jms._

class BlendedJMSConnection(vendor : String, provider : String, conn : Connection) extends Connection {

  protected[utils] def connection : Connection = conn

  override def createDurableConnectionConsumer(topic : Topic, s : String, s1 : String, serverSessionPool : ServerSessionPool, i : Int) : ConnectionConsumer =
    conn.createDurableConnectionConsumer(topic, s, s1, serverSessionPool, i)

  override def stop() : Unit = {}

  override def createSession(b : Boolean, i : Int) : Session = conn.createSession(b, i)

  override def getClientID : String = conn.getClientID

  override def createConnectionConsumer(
    destination : Destination, s : String,
    serverSessionPool : ServerSessionPool,
    i : Int
  ) : ConnectionConsumer = conn.createConnectionConsumer(destination, s, serverSessionPool, i)

  override def getMetaData : ConnectionMetaData = conn.getMetaData

  override def setExceptionListener(exceptionListener : ExceptionListener) : Unit = conn.setExceptionListener(exceptionListener)

  override def setClientID(s : String) : Unit = conn.setClientID(s)

  override def getExceptionListener : ExceptionListener = conn.getExceptionListener

  override def close() : Unit = {}

  override def start() : Unit = conn.start()

  override def toString: String = s"BlendedJMSConnection($vendor, $provider, $getClientID)"
}
