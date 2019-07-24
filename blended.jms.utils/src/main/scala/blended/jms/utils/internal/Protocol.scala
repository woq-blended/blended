package blended.jms.utils.internal

import java.util.Date

import akka.actor.ActorRef
import javax.jms.Connection

import scala.concurrent.duration.FiniteDuration

case class CheckConnection(now : Boolean)
case object ConnectionClosed
case object CloseTimeout
case class ConnectTimeout(t: Long)

case class ExecutePing(pingActor: ActorRef, id: AnyVal)

/**
  * Message hierarchy to indicate the outcome of a Ping. A successful ping will
  * simply be the id of the ping message, otherwise we will get the uderlying
  * exception
  */
sealed trait PingResult
case object PingPending extends PingResult
case object PingTimeout extends PingResult
case class PingSuccess(msg: String) extends PingResult
case class PingFailed(t: Throwable) extends PingResult

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
