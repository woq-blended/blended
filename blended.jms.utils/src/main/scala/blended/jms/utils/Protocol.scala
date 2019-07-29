package blended.jms.utils

import java.util.Date

import javax.jms.Connection

import scala.concurrent.duration.FiniteDuration

case class CheckConnection(now : Boolean)
case object ConnectionClosed
case object CloseTimeout
case class ConnectTimeout(t : Long)

sealed trait KeepAliveEvent
case class AddedConnectionFactory(cfg : ConnectionConfig) extends KeepAliveEvent
case class RemovedConnectionFactory(cfg : ConnectionConfig) extends KeepAliveEvent
case class MesssageReceived(cf : IdAwareConnectionFactory) extends KeepAliveEvent
case class KeepAliveMissed(cf : IdAwareConnectionFactory, count : Int) extends KeepAliveEvent
case class MaxKeepAliveExceeded(vendor : String, provider : String) extends KeepAliveEvent

/**
 * Command message to establish a JMS Connection
 */
case class Connect(ts : Date, id : String)

/**
 * Command message to close a JMS Connection within a given timeout
 */
case class Disconnect(timeout : FiniteDuration)

/**
 * Outcome of a connect
 */
case class ConnectResult(ts : Date, r : Either[Throwable, Connection])

case class ConnectionStateChanged(state : ConnectionState)

case class ConnectionCommand(
  vendor : String,
  provider : String,
  maxEvents : Int = 0,
  disconnectPending : Boolean = false,
  connectPending : Boolean = false,
  reconnectNow : Boolean = false
)

object Reconnect {
  def apply(cf : IdAwareConnectionFactory, e : Option[Throwable]) : Reconnect =
    new Reconnect(cf.vendor, cf.provider, e)
}

case class Reconnect(vendor : String, provider : String, e : Option[Throwable])
