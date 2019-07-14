package blended.jms.utils

import java.util.Date

object ConnectionState {

  val CONNECTED = "connected"
  val CONNECTING = "connecting"
  val CLOSING = "closing"
  val DISCONNECTED = "disconnected"

  val defaultMaxEvents : Int = 20
}

case class ConnectionState(
  vendor : String,
  provider : String,
  status : String = ConnectionState.DISCONNECTED,
  lastConnect : Option[Date] = None,
  lastDisconnect : Option[Date] = None,
  failedPings : Int = 0,
  maxEvents : Int = ConnectionState.defaultMaxEvents,
  events : List[String] = List.empty,
  firstReconnectAttempt : Option[Date] = None,
  lastConnectAttempt : Option[Date] = None
)
