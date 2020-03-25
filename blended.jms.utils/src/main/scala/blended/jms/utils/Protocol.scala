package blended.jms.utils

import java.util.Date

import akka.actor.ActorRef
import javax.jms.Connection

import scala.concurrent.duration.FiniteDuration

case class CheckConnection(now : Boolean)
case object ConnectionClosed
case object CloseTimeout
case class ConnectTimeout(t : Date)

sealed trait KeepAliveEvent
case class AddedConnectionFactory(cfg : IdAwareConnectionFactory) extends KeepAliveEvent
case class RemovedConnectionFactory(cfg : IdAwareConnectionFactory) extends KeepAliveEvent
case class MessageReceived(vendor : String, provider : String, id : String) extends KeepAliveEvent
case class ProducerMaterialized(vendor: String, provider: String, prod : ActorRef)
case class KeepAliveMissed(vendor : String, provider : String, count : Int) extends KeepAliveEvent
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

case class QueryConnectionState(
  vendor : String,
  provider : String
)

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
