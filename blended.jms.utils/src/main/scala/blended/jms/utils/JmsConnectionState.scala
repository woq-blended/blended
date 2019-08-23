package blended.jms.utils

import java.util.Date

sealed trait JmsConnectionState {
  val name : String = getClass().getSimpleName()
  override def toString: String = name
}

case object Connected extends JmsConnectionState
case object Connecting extends JmsConnectionState
case object Closing extends JmsConnectionState
case object Disconnected extends JmsConnectionState
case class RestartContainer(reason : Throwable) extends JmsConnectionState

//scalastyle:off magic.number
case class ConnectionState(
  vendor : String,
  provider : String,
  status : JmsConnectionState = Disconnected,
  lastConnect : Option[Date] = None,
  lastDisconnect : Option[Date] = None,
  missedKeepAlives : Int = 0,
  maxEvents : Int = 20,
  events : List[String] = List.empty,
  firstReconnectAttempt : Option[Date] = None,
  lastConnectAttempt : Option[Date] = None
) {
  override def toString: String = s"${getClass().getSimpleName()}(vendor=$vendor, provider=$provider, status=$status," +
    s" lastConnect=$lastConnect, lastDisconnect=$lastDisconnect, missedKeepAlives=$missedKeepAlives," +
    s" firstReconnectAttempt=$firstReconnectAttempt, lastConnectAttempt=$lastConnectAttempt)"
}
//scalastyle:on magic.number
