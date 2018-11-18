package blended.activemq.brokerstarter.internal

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{BlendedJMSConnectionConfig, ConnectionConfig}
import blended.util.config.Implicits._
import com.typesafe.config.Config
import org.apache.activemq.ActiveMQConnectionFactory

import scala.util.Try

case class BrokerConfig (
  override val vendor : String,
  override val provider : String,
  override val clientId : String,
  override val jmxEnabled : Boolean,
  override val pingEnabled : Boolean,
  override val pingTolerance : Int,
  override val pingDestination : String,
  override val pingInterval : Int,
  override val pingTimeout : Int,
  override val minReconnect : Int,
  override val maxReconnectTimeout : Int,
  override val properties : Map[String, String],
  override val retryInterval : Int,
  brokerName : String,
  file : String,
  withSsl : Boolean,
) extends ConnectionConfig {
  override val enabled: Boolean = true
  override val defaultUser: Option[String] = None
  override val defaultPassword: Option[String] = None
  override val useJndi: Boolean = false
  override val cfEnabled: Option[ConnectionConfig => Boolean] = None
  override val cfClassName: Option[String] = Some(classOf[ActiveMQConnectionFactory].getName())
  override val ctxtClassName: Option[String] = None
  override val jmsClassloader: Option[ClassLoader] = None
}

object BrokerConfig {

  import BlendedJMSConnectionConfig._

  private val getAndResolve : (String => Try[Any]) => Config => String => Option[String] = resolve => cfg => propName =>
    cfg.getStringOption(propName).map(s => resolve(s).get).map(_.toString)


  val name : (String => Try[Any]) => Config => Option[String] = resolve => cfg =>
    getAndResolve(resolve)(cfg)("brokerName")

  val vendor : (String => Try[Any]) => Config => Option[String] = resolve => cfg =>
    getAndResolve(resolve)(cfg)("vendor")

  val provider : (String => Try[Any]) => Config => Option[String] = resolve => cfg =>
    getAndResolve(resolve)(cfg)("provider")

  val file : (String => Try[Any]) => Config => Option[String] = resolve => cfg =>
    getAndResolve(resolve)(cfg)("file")

  val ssl : Config => Boolean = cfg => cfg.getBoolean("withSsl", true)

  def create(brokerName : String, idSvc: ContainerIdentifierService, cfg: Config) : Try[BrokerConfig] = Try {

    val resolve : String => Try[Any] = value => idSvc.resolvePropertyString(value)

    BrokerConfig(
      vendor = vendor(resolve)(cfg).getOrElse("activemq"),
      provider = provider(resolve)(cfg).getOrElse("activemq"),
      clientId = clientId(resolve)(cfg).get,
      jmxEnabled = jmxEnabled(cfg),
      pingEnabled = pingEnabled(cfg),
      pingTolerance = pingTolerance(cfg),
      pingDestination = destination(cfg),
      pingInterval = pingInterval(cfg),
      pingTimeout = pingTimeout(cfg),
      minReconnect = minReconnect(cfg),
      maxReconnectTimeout = maxReconnectTimeout(cfg),
      properties = properties(resolve)(cfg).get,
      retryInterval = retryInterval(cfg),

      brokerName = name(resolve)(cfg).getOrElse(brokerName),
      file = file(resolve)(cfg).getOrElse(s"$brokerName.amq"),
      withSsl = ssl(cfg)
    )
  }
}
