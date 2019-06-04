package blended.jms.utils

import scala.concurrent.duration.FiniteDuration

trait ConnectionConfig {

  val vendor : String
  val provider : String
  val enabled : Boolean
  val jmxEnabled : Boolean
  val pingEnabled : Boolean
  val pingTolerance : Int
  val pingInterval : FiniteDuration
  val pingTimeout : FiniteDuration
  val retryInterval : FiniteDuration
  val minReconnect : FiniteDuration
  val maxReconnectTimeout : Option[FiniteDuration]
  val clientId : String
  val defaultUser : Option[String]
  val defaultPassword : Option[String]
  val pingDestination : String
  val properties : Map[String, String]
  val useJndi : Boolean
  val jndiName : Option[String] = None
  val cfEnabled : Option[ConnectionConfig => Boolean]
  val cfClassName : Option[String]
  val ctxtClassName : Option[String]
  val jmsClassloader : Option[ClassLoader]
}
