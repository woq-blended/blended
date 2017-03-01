package blended.jms.utils.internal

import javax.jms.Connection

import scala.concurrent.duration.FiniteDuration

case object CheckConnection
case object ConnectionClosed
case object CloseTimeout
case class ConnectTimeout(t: Long)

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
case class Connect(t : Long)

/**
  * Command message to close a JMS Connection within a given timeout
  * @param timeout
  */
case class Disconnect(timeout: FiniteDuration)

/**
  * Outcome of a connect
  * @param t
  * @param r
  */
case class ConnectResult(t: Long, r : Either[Throwable, Connection])

case class ConnectionStateChanged(state: ConnectionState)
