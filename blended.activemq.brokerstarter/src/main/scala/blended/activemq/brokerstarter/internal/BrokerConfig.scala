package blended.activemq.brokerstarter.internal

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{BlendedJMSConnectionConfig, ConnectionConfig}
import blended.util.config.Implicits._
import com.typesafe.config.Config
import org.apache.activemq.ActiveMQConnectionFactory

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

case class BrokerConfig (
  override val vendor : String,
  override val provider : String,
  override val clientId : String,
  override val jmxEnabled : Boolean,
  override val pingEnabled : Boolean,
  override val pingTolerance : Int,
  override val pingDestination : String,
  override val pingInterval : FiniteDuration,
  override val pingTimeout : FiniteDuration,
  override val minReconnect : FiniteDuration,
  override val maxReconnectTimeout : Option[FiniteDuration],
  override val properties : Map[String, String],
  override val retryInterval : FiniteDuration,
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

    val v = vendor(resolve)(cfg).getOrElse("activemq")
    val p = provider(resolve)(cfg).getOrElse("activemq")

    val jmsConfig : BlendedJMSConnectionConfig = BlendedJMSConnectionConfig.fromConfig(resolve)(v, p, cfg)

    BrokerConfig(
      vendor = jmsConfig.vendor,
      provider = jmsConfig.provider,
      clientId = jmsConfig.clientId,
      jmxEnabled = jmsConfig.jmxEnabled,
      pingEnabled = jmsConfig.pingEnabled,
      pingTolerance = jmsConfig.pingTolerance,
      pingDestination = jmsConfig.pingDestination,
      pingInterval = jmsConfig.pingInterval,
      pingTimeout = jmsConfig.pingTimeout,
      minReconnect = jmsConfig.minReconnect,
      maxReconnectTimeout = jmsConfig.maxReconnectTimeout,
      properties = jmsConfig.properties,
      retryInterval = jmsConfig.retryInterval,

      brokerName = name(resolve)(cfg).getOrElse(brokerName),
      file = file(resolve)(cfg).getOrElse(s"$brokerName.amq"),
      withSsl = ssl(cfg)
    )
  }
}
