package blended.jms.utils

import java.util.Date

import akka.actor.ActorRef

sealed trait JmsConnectionState

case object Connected extends JmsConnectionState { override def toString : String = "Connected" }
case object Connecting extends JmsConnectionState { override def toString : String = "Connecting"}
case object Closing extends JmsConnectionState { override def toString  : String = "Closing"}
case object Disconnected extends JmsConnectionState { override def toString : String = "Disconnected" }
case class RestartContainer(reason : Throwable) extends JmsConnectionState {
  override def toString : String = s"RestartContainer(${reason.getMessage()})"
}

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
  lastConnectAttempt : Option[Date] = None,
  controller : Option[ActorRef] = None
) {
  override def toString: String = s"${getClass().getSimpleName()}(vendor=$vendor, provider=$provider, status=$status," +
    s" lastConnect=$lastConnect, lastDisconnect=$lastDisconnect, missedKeepAlives=$missedKeepAlives," +
    s" firstReconnectAttempt=$firstReconnectAttempt, lastConnectAttempt=$lastConnectAttempt)"
}
//scalastyle:on magic.number
