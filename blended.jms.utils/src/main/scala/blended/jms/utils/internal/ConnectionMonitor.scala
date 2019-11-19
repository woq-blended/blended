package blended.jms.utils.internal
import java.text.SimpleDateFormat

import blended.jms.utils.{ConnectionCommand, ConnectionState, Disconnected}

class ConnectionMonitor(vendor : String, provider : String, clientId : String) extends ConnectionMonitorMXBean {

  private[this] val df = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS")
  private[this] var state : ConnectionState = ConnectionState(vendor = vendor, provider = provider).copy(status = Disconnected)

  private[this] var cmd : ConnectionCommand = ConnectionCommand(vendor = vendor, provider = provider)

  override def getVendor() : String = vendor
  override def getProvider() : String = provider
  override def getClientId() : String = clientId

  def getCommand() : ConnectionCommand = cmd
  def resetCommand() : Unit = { cmd = ConnectionCommand(vendor = vendor, provider = provider) }

  def setState(newState : ConnectionState) : Unit = { state = newState }
  def getState() : ConnectionState = state

  override def getStatus() : String = state.status.toString()

  override def getLastConnect() : String = state.lastConnect match {
    case None    => "n/a"
    case Some(d) => df.format(d)
  }

  override def getLastDisconnect() : String = state.lastDisconnect match {
    case None    => "n/a"
    case Some(d) => df.format(d)
  }

  override def getMissedKeepAlives() : Int = state.missedKeepAlives

  override def getMaxEvents() : Int = state.maxEvents

  override def setMaxEvents(n : Int) : Unit = { cmd = cmd.copy(maxEvents = n) }

  override def getEvents() : Array[String] = state.events.toArray

  override def disconnect(reason : String) : Unit = { cmd = cmd.copy(disconnectPending = true) }

  override def connect(now : Boolean) : Unit = { cmd = cmd.copy(connectPending = true, reconnectNow = now) }
}
