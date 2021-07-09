package blended.jms.utils

import scala.concurrent.duration.FiniteDuration
import javax.jms.ConnectionFactory

trait ConnectionConfig {

  val vendor: String
  val provider: String
  val enabled: Boolean
  val jmxEnabled: Boolean
  val keepAliveEnabled: Boolean
  val maxKeepAliveMissed: Int
  val keepAliveInterval: FiniteDuration
  val minReconnect: FiniteDuration
  val maxReconnectTimeout: Option[FiniteDuration]
  val connectTimeout: FiniteDuration
  val retryInterval: FiniteDuration
  val clientId: String
  val defaultUser: Option[String]
  val defaultPassword: Option[String]
  val keepAliveDestination: String
  val properties: Map[String, String]
  val useJndi: Boolean
  val jndiName: Option[String] = None
  val cfEnabled: Option[ConnectionConfig => Boolean]
  val cf: Option[ConnectionFactory]
  val ctxtClassName: Option[String]
  val jmsClassloader: Option[ClassLoader]
}
