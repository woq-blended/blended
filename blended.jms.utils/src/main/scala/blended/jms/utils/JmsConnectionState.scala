package blended.jms.utils

import java.util.Date

sealed trait JmsConnectionState {
  val name : String
  override def toString: String = name
}

case object Connected extends JmsConnectionState{ override val name : String = "Connected" }
case object Connecting extends JmsConnectionState { override val name : String = "Connecting" }
case object Closing extends JmsConnectionState { override val name : String = "Closing" }
case object Disconnected extends JmsConnectionState { override val name : String = "Disonnected" }
case class RestartContainer(reason : Throwable) extends JmsConnectionState { override val name = "RestartContainer"}

//scalastyle:off magic.number
case class ConnectionState(
  vendor : String,
  provider : String,
  status : JmsConnectionState = Disconnected,
  lastConnect : Option[Date] = None,
  lastDisconnect : Option[Date] = None,
  failedPings : Int = 0,
  maxEvents : Int = 20,
  events : List[String] = List.empty,
  firstReconnectAttempt : Option[Date] = None,
  lastConnectAttempt : Option[Date] = None
)
//scalastyle:on magic.number
