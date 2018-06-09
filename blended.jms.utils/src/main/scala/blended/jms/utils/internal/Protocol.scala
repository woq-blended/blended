package blended.jms.utils.internal

import java.util.Date

import akka.actor.ActorRef
import javax.jms.{Connection, JMSException}

import scala.concurrent.duration.FiniteDuration

case class CheckConnection(now : Boolean)
case object ConnectionClosed
case object CloseTimeout
case class ConnectTimeout(t: Long)

case class ExecutePing(pingActor: ActorRef)

case object PingTimeout

/**
  * Message to indicate the outcome of a Ping. A successful ping will
  * simply be the id of the ping message, otherwise we will get the uderlying
  * exception
  * @param result
  */
case class PingResult(result : Either[Throwable, String])

/**
  * Message to indicate the successful reception of a ping. Used by the #ConnectionPingActor
  * @param s
  */
case class PingReceived(s : String)

/**
  * Command message to restart the container in case of an exception that can't be recovered.
  * @param reason The underlying reason to restart
  */
case class RestartContainer(reason: Throwable)

/**
  * Command message to establish a JMS Connection
  */
case class Connect(ts : Date, id: String)

/**
  * Command message to close a JMS Connection within a given timeout
  * @param timeout
  */
case class Disconnect(timeout: FiniteDuration)

/**
  * Outcome of a connect
  */
case class ConnectResult(ts: Date, r : Either[Throwable, Connection])

case class ConnectionStateChanged(state: ConnectionState)

case class ConnectionCommand(
  vendor: String,
  provider: String,
  maxEvents: Int = 0,
  disconnectPending : Boolean = false,
  connectPending: Boolean = false,
  reconnectNow : Boolean = false
)
