package blended.streams.jms

import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue, JmsTopic}
import blended.streams.FlowHeaderConfig
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.util.logging.LogLevel
import blended.util.logging.LogLevel.LogLevel
import javax.jms
import javax.jms.Session

import scala.concurrent.duration._
import scala.util.Try

final class AcknowledgeMode(val mode : Int) {

  override def toString : String = {
    val modeName = mode match {
      case Session.AUTO_ACKNOWLEDGE    => "AutoAcknowldge"
      case Session.CLIENT_ACKNOWLEDGE  => "ClientAcknowledge"
      case Session.DUPS_OK_ACKNOWLEDGE => "DupsOkAcknowledge"
      case Session.SESSION_TRANSACTED  => "SessionTransacted"
    }

    s"${getClass().getSimpleName()}($modeName)"
  }
}

object AcknowledgeMode {
  val AutoAcknowledge : AcknowledgeMode = new AcknowledgeMode(Session.AUTO_ACKNOWLEDGE)
  val ClientAcknowledge : AcknowledgeMode = new AcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
  val DupsOkAcknowledge : AcknowledgeMode = new AcknowledgeMode(Session.DUPS_OK_ACKNOWLEDGE)
  val SessionTransacted : AcknowledgeMode = new AcknowledgeMode(Session.SESSION_TRANSACTED)
}

final class JmsDeliveryMode(val mode : Int) {

  def asString : String = mode match {
    case jms.DeliveryMode.PERSISTENT     => "Persistent"
    case jms.DeliveryMode.NON_PERSISTENT => "NonPersistent"
    case _                               => "Unknown"
  }

  override def toString : String = s"${getClass().getSimpleName()}($asString)"
}

object JmsDeliveryMode {
  val NonPersistent = new JmsDeliveryMode(jms.DeliveryMode.NON_PERSISTENT)
  val Persistent = new JmsDeliveryMode(jms.DeliveryMode.PERSISTENT)

  def create(m : String) : Try[JmsDeliveryMode] = Try {
    m match {
      case "Persistent"    => Persistent
      case "NonPersistent" => NonPersistent
      case _               => throw new IllegalArgumentException(s"Unknown Persistence mode : [$m]")
    }
  }
}

object JmsSettings {
  val defaultHeaderPrefix = "Blended"
}

sealed trait JmsSettings {

  // The underlying JMS Connection Factory
  def connectionFactory : IdAwareConnectionFactory

  // A Connection Timeout, a JMS stage configured with these settings will
  // terminate with an error if a connection is not possible within the
  // specified timeout
  val connectionTimeout : FiniteDuration

  // A key handler strategy that can translate property names in JMS messages
  // if required
  val keyFormatStrategy : JmsKeyFormatStrategy

  // An optional JMS Destination, it depends on the Jms Stage how this destination
  // is used
  val jmsDestination : Option[JmsDestination]

  // The number of sessions used by the stage using this configuration
  val sessionCount : Int

  // The header configuration which encapsulates the defined header names being used in the
  // flow message
  val headerCfg : FlowHeaderConfig

  // A logger, so that it can be injected rather than being created based on the classname only
  val log : FlowEnvelopeLogger

  // Determine the log level for any message received / message sent events from the envelope
  val logLevel : FlowEnvelope => LogLevel
}

final case class JmsConsumerSettings(
  override val log: FlowEnvelopeLogger,
  override val logLevel: FlowEnvelope => LogLevel = _ => LogLevel.Info,
  override val headerCfg : FlowHeaderConfig,
  override val keyFormatStrategy: JmsKeyFormatStrategy = new DefaultKeyFormatStrategy(),
  connectionFactory : IdAwareConnectionFactory,
  connectionTimeout : FiniteDuration = 1.second,
  jmsDestination : Option[JmsDestination] = None,
  sessionCount : Int = 1,
  receiveTimeout : FiniteDuration = 0.seconds,
  pollInterval : FiniteDuration = 100.millis,
  acknowledgeMode: AcknowledgeMode = AcknowledgeMode.AutoAcknowledge,
  bufferSize: Int = 100,
  selector: Option[String] = None,
  ackTimeout: FiniteDuration = 1.second,
  durableName: Option[String] = None
) extends JmsSettings {

  def withDestination(dest : Option[JmsDestination]) : JmsConsumerSettings = copy(jmsDestination = dest)
  def withQueue(name : String) : JmsConsumerSettings = copy(jmsDestination = Some(JmsQueue(name)))
  def withTopic(name : String) : JmsConsumerSettings = copy(jmsDestination = Some(JmsTopic(name)))

  def withAcknowledgeMode(m : AcknowledgeMode) : JmsConsumerSettings = copy(acknowledgeMode = m)
  def withSessionCount(c : Int) : JmsConsumerSettings = copy(sessionCount = c)
  def withSelector(s : Option[String]) : JmsConsumerSettings = copy(selector = s)
  def withAckTimeout(d : FiniteDuration) : JmsConsumerSettings = copy(ackTimeout = d)
  def withConnectionTimeout(d : FiniteDuration) : JmsConsumerSettings = copy(connectionTimeout = d)

  def withSubScriberName(name : Option[String]) : JmsConsumerSettings = copy(durableName = name)
}

object JmsConsumerSettings {
  def create(
    log : FlowEnvelopeLogger,
    cf: IdAwareConnectionFactory,
    headerConfig: FlowHeaderConfig
  ) : JmsConsumerSettings =
    JmsConsumerSettings(
      log = log, headerCfg = headerConfig, connectionFactory = cf
    )
}

final case class JmsProducerSettings(
  override val log : FlowEnvelopeLogger,
  override val logLevel: FlowEnvelope => LogLevel = _ => LogLevel.Info,
  override val headerCfg : FlowHeaderConfig,
  override val keyFormatStrategy: JmsKeyFormatStrategy = new DefaultKeyFormatStrategy(),
  connectionFactory : IdAwareConnectionFactory,
  connectionTimeout : FiniteDuration = 1.second,
  jmsDestination: Option[JmsDestination] = None,
  sessionCount: Int = 1,
  // Should we evaluate the mesage for send parameters ?
  destinationResolver  : JmsProducerSettings => JmsDestinationResolver = s => new SettingsDestinationResolver(s),
  // the priority to used as default
  priority : Int = 4,
  // the delivery mode to be used as a default
  deliveryMode : JmsDeliveryMode = JmsDeliveryMode.NonPersistent,
  // the time to live to be used as a default
  timeToLive : Option[FiniteDuration] = None,
  // A factory for correlation Ids in case no Correlation Id is set in the message
  correlationId : () => Option[String] = () => None,
  clearPreviousException : Boolean = false
) extends JmsSettings {

  def withDestinationResolver(f : JmsProducerSettings => JmsDestinationResolver) : JmsProducerSettings = copy(destinationResolver = f)

  def withDestination(dest : Option[JmsDestination]) : JmsProducerSettings = copy(jmsDestination = dest)
  def withQueue(name : String) : JmsProducerSettings = copy(jmsDestination = Some(JmsQueue(name)))
  def withTopic(name : String) : JmsProducerSettings = copy(jmsDestination = Some(JmsTopic(name)))

  def withConnectionTimeout(d : FiniteDuration) : JmsProducerSettings = copy(connectionTimeout = d)
  def withSessionCount(count : Int) : JmsProducerSettings = copy(sessionCount = count)
  def withPriority(p : Int) : JmsProducerSettings = copy(priority = p)

  def withTimeToLive(ttl : java.time.Duration) : JmsProducerSettings = copy(timeToLive = Some(Duration.fromNanos(ttl.toNanos)))
  def withTimeToLive(ttl : Option[FiniteDuration]) : JmsProducerSettings = copy(timeToLive = ttl)
  def withTimeToLive(ttl : Long, unit : TimeUnit) : JmsProducerSettings = copy(timeToLive = Some(Duration(ttl, unit)))

  def withDeliveryMode(m : JmsDeliveryMode) : JmsProducerSettings = copy(deliveryMode = m)

  override def toString : String = s"{${getClass().getSimpleName()}(cf=${connectionFactory.id}, connTimeout=$connectionTimeout, dest=$jmsDestination, " +
    s"priority=$priority, delMode=${deliveryMode.asString}, ttl=$timeToLive)"

}

object JmsProducerSettings {

  def create(log : FlowEnvelopeLogger, connectionFactory: IdAwareConnectionFactory, headerConfig : FlowHeaderConfig) : JmsProducerSettings =
    JmsProducerSettings(log = log, headerCfg = headerConfig, connectionFactory = connectionFactory)
}
