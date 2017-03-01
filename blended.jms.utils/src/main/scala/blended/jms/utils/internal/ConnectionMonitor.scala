package blended.jms.utils.internal
import java.util.Date

class ConnectionMonitor(provider: String) extends ConnectionMonitorMBean {

  private[this] var state : ConnectionState = ConnectionState()

  override def getProvider(): String = provider

  def setState(newState: ConnectionState) : Unit = { state = newState }
  override def getState(): String = state.state

  override def getLastConnect(): Option[Date] = state.lastConnect

  override def getLastDisconnect(): Option[Date] = state.lastDisconnect

  override def getFailedPings(): Int = state.failedPings

  override def getMaxEvents(): Int = state.maxEvents

  override def setMaxEvents(n: Int): Unit = {} // TODO

  override def getEvents(): Array[String] = state.events.toArray
}
