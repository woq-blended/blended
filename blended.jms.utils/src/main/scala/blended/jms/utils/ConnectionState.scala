package blended.jms.utils

import java.util.Date

import blended.jms.utils.ConnectionState.Disconnected

object ConnectionState {

  sealed trait State {
    val name : String
    override def toString: String = name
  }
  case object Connected extends State{ override val name : String = "Connected" }
  case object Connecting extends State{ override val name : String = "Connecting" }
  case object Closing extends State{ override val name : String = "Closing" }
  case object Disconnected extends State{ override val name : String = "Disonnected" }
  case class RestartContainer(reason : Throwable) extends State{ override val name = "RestartContainer"}

  val defaultMaxEvents : Int = 20
}

case class ConnectionState(
  vendor : String,
  provider : String,
  status : ConnectionState.State = Disconnected,
  lastConnect : Option[Date] = None,
  lastDisconnect : Option[Date] = None,
  failedPings : Int = 0,
  maxEvents : Int = ConnectionState.defaultMaxEvents,
  events : List[String] = List.empty,
  firstReconnectAttempt : Option[Date] = None,
  lastConnectAttempt : Option[Date] = None
)
