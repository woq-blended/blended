package blended.jms.utils.internal

import java.util.Date

object ConnectionState {

  val CONNECTED = "connected"
  val CONNECTING = "connecting"
  val CLOSING = "closing"
  val DISCONNECTED = "disconnected"
}

case class ConnectionState(
  provider : String,
  status : String = ConnectionState.DISCONNECTED,
  lastConnect : Option[Date] = None,
  lastDisconnect : Option[Date] = None,
  failedPings : Int = 0,
  maxEvents : Int = 20,
  events : List[String] = List.empty,
  firstReconnectAttempt : Option[Date] = None,
  lastConnectAttempt : Option[Date] = None
)
