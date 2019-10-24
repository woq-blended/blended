package blended.activemq.brokerstarter.internal

import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{BlendedJMSConnectionConfig, ConnectionConfig}
import blended.util.config.Implicits._
import com.typesafe.config.Config
import org.apache.activemq.ActiveMQConnectionFactory
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

case class BrokerConfig(
  override val vendor : String,
  override val provider : String,
  override val clientId : String,
  override val jmxEnabled : Boolean,
  override val keepAliveEnabled : Boolean,
  override val maxKeepAliveMissed : Int,
  override val keepAliveInterval : FiniteDuration,
  override val retryInterval : FiniteDuration,
  override val connectTimeout: FiniteDuration,
  override val keepAliveDestination : String,
  override val minReconnect : FiniteDuration,
  override val maxReconnectTimeout : Option[FiniteDuration],
  override val properties : Map[String, String],
  override val defaultUser : Option[String],
  override val defaultPassword : Option[String],
  brokerName : String,
  file : String,
  withSsl : Boolean,
  withAuthentication : Boolean,
  anonymousUser : Option[String],
  anonymousGroups : List[String]
) extends ConnectionConfig {
  override val enabled : Boolean = true
  override val useJndi : Boolean = false
  override val cfEnabled : Option[ConnectionConfig => Boolean] = None
  override val cfClassName : Option[String] = Some(classOf[ActiveMQConnectionFactory].getName())
  override val ctxtClassName : Option[String] = None
  override val jmsClassloader : Option[ClassLoader] = None
}

object BrokerConfig {

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

  val authenticate : Config => Boolean = cfg => cfg.getBoolean("withAuthentication", false)

  val anonymous : Config => Option[String] = cfg => cfg.getStringOption("anonymousUser")

  val anonymousGroups : Config => List[String] = cfg => cfg.getStringList("anonymousGroups", List.empty)

  def create(brokerName : String, idSvc : ContainerIdentifierService, cfg : Config) : Try[BrokerConfig] = Try {

    val resolve : String => Try[Any] = value => idSvc.resolvePropertyString(value)

    val v = vendor(resolve)(cfg).getOrElse("activemq")
    val p = provider(resolve)(cfg).getOrElse("activemq")

    val jmsConfig : BlendedJMSConnectionConfig = BlendedJMSConnectionConfig.fromConfig(resolve)(v, p, cfg)

    BrokerConfig(
      vendor = jmsConfig.vendor,
      provider = jmsConfig.provider,
      defaultUser = jmsConfig.defaultUser,
      defaultPassword = jmsConfig.defaultPassword,
      clientId = jmsConfig.clientId,
      jmxEnabled = jmsConfig.jmxEnabled,
      keepAliveEnabled = jmsConfig.keepAliveEnabled,
      maxKeepAliveMissed = jmsConfig.maxKeepAliveMissed,
      keepAliveInterval = jmsConfig.keepAliveInterval,
      retryInterval = jmsConfig.retryInterval,
      connectTimeout = jmsConfig.connectTimeout,
      keepAliveDestination = jmsConfig.keepAliveDestination,
      minReconnect = jmsConfig.minReconnect,
      maxReconnectTimeout = jmsConfig.maxReconnectTimeout,
      properties = jmsConfig.properties,
      brokerName = name(resolve)(cfg).getOrElse(brokerName),
      file = file(resolve)(cfg).getOrElse(s"$brokerName.amq"),
      withSsl = ssl(cfg),
      withAuthentication = authenticate(cfg),
      anonymousUser = anonymous(cfg),
      anonymousGroups = anonymousGroups(cfg)
    )
  }
}
