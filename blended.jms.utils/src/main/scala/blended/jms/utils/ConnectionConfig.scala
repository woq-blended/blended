package blended.jms.utils

trait ConnectionConfig {

  val vendor : String
  val provider : String
  val enabled : Boolean
  val jmxEnabled : Boolean
  val pingEnabled : Boolean
  val pingTolerance : Int
  val pingInterval : Int
  val pingTimeout : Int
  val retryInterval : Int
  val minReconnect : Int
  val maxReconnectTimeout: Int
  val clientId : String
  val defaultUser : Option[String]
  val defaultPassword : Option[String]
  val pingDestination : String
  val properties : Map[String, String]
  val useJndi : Boolean
  val jndiName : Option[String] = None
  val cfEnabled : Option[ConnectionConfig => Boolean]
  val cfClassName: Option[String]
  val ctxtClassName : Option[String]
  val jmsClassloader : Option[ClassLoader]
}
