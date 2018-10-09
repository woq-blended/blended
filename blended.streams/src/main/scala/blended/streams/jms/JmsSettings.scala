package blended.streams.jms

import blended.jms.utils.{JmsDestination, JmsQueue, JmsTopic}
import javax.jms
import javax.jms.{ConnectionFactory, Session}

import scala.concurrent.duration._
import scala.util.Try

final class AcknowledgeMode(val mode: Int)

object AcknowledgeMode {
  val AutoAcknowledge: AcknowledgeMode = new AcknowledgeMode(Session.AUTO_ACKNOWLEDGE)
  val ClientAcknowledge: AcknowledgeMode = new AcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
  val DupsOkAcknowledge: AcknowledgeMode = new AcknowledgeMode(Session.DUPS_OK_ACKNOWLEDGE)
  val SessionTransacted: AcknowledgeMode = new AcknowledgeMode(Session.SESSION_TRANSACTED)
}

final class JmsDeliveryMode(val mode: Int) {
  override def toString: String = {
    val sMode = mode match {
      case jms.DeliveryMode.PERSISTENT => "Persistent"
      case jms.DeliveryMode.NON_PERSISTENT => "NonPersistent"
      case _ => "Unknown"
    }
    s"${getClass().getSimpleName()}($sMode)"
  }
}

object JmsDeliveryMode {
  val NonPersistent = new JmsDeliveryMode(jms.DeliveryMode.NON_PERSISTENT)
  val Persistent = new JmsDeliveryMode(jms.DeliveryMode.PERSISTENT)

  def create(m : String) : Try[JmsDeliveryMode] = Try {
    m match {
      case "Persistent" => Persistent
      case "NonPersistent" => NonPersistent
      case _ => throw new IllegalArgumentException(s"Unknown Persistence mode : [$m]")
    }
  }
}

object JmsSettings {
  val defaultHeaderPrefix = "Blended"
}

sealed trait JmsSettings {
  // The underlying JMS Connection Factory
  def connectionFactory: ConnectionFactory

  // A Connection Timeout, a JMS stage configured with these settings will
  // terminate with an error if a connection is not possible within the
  // specified timeout
  val connectionTimeout: FiniteDuration

  // An optional JMS Destination, it depends on the Jms Stage how this destination
  // is used
  val jmsDestination : Option[JmsDestination]

  // The number of sessions used by the stage using this configuration
  val sessionCount : Int

  // A prefix used for any Headernames, the infrastructure puts into a FlowMessage
  val headerPrefix : String
}

final case class JMSConsumerSettings(
  connectionFactory: ConnectionFactory,
  connectionTimeout : FiniteDuration = 1.second,
  jmsDestination: Option[JmsDestination] = None,
  sessionCount: Int = 1,
  headerPrefix : String = JmsSettings.defaultHeaderPrefix,
  acknowledgeMode: AcknowledgeMode = AcknowledgeMode.AutoAcknowledge,
  bufferSize: Int = 100,
  selector: Option[String] = None,
  ackTimeout: FiniteDuration = 100.millis,
  durableName: Option[String] = None
) extends JmsSettings {

  def withConnectionTimeout(d : FiniteDuration): JMSConsumerSettings = copy(connectionTimeout = d)

  def noDestination() : JMSConsumerSettings = copy(jmsDestination = None)
  def withDestination(d: JmsDestination) : JMSConsumerSettings = copy(jmsDestination = Some(d))

  def withAcknowledgeMode(m: AcknowledgeMode): JMSConsumerSettings = copy(acknowledgeMode = m)
  def withSessionCount(c : Int): JMSConsumerSettings = copy(sessionCount = c)

  def withBufferSize(s : Int): JMSConsumerSettings = copy(bufferSize = s)

  def noSelector(): JMSConsumerSettings = copy(selector = None)
  def withSelector(s : String): JMSConsumerSettings = copy(selector = Some(s))

  def withAckTimeout(d : FiniteDuration): JMSConsumerSettings = copy(ackTimeout = d)

  def noDurableName(): JMSConsumerSettings = copy(durableName = None)
  def withDurableName(s: String): JMSConsumerSettings = copy(durableName = Some(s))
}

object JMSConsumerSettings {
  def create(cf: ConnectionFactory) : JMSConsumerSettings = JMSConsumerSettings(cf)
}

final case class JmsProducerSettings(
  connectionFactory: ConnectionFactory,
  connectionTimeout : FiniteDuration = 1.second,
  jmsDestination: Option[JmsDestination] = None,
  sessionCount: Int = 1,
  headerPrefix : String = JmsSettings.defaultHeaderPrefix,
  // Should we evaluate the mesage for send parameters ?
  sendParamsFromMessage : Boolean = true,
  // the priority to used as default
  priority : Int = 4,
  // the delivery mode to be used as a default
  deliveryMode : JmsDeliveryMode = JmsDeliveryMode.NonPersistent,
  // the time to live to be used as a default
  timeToLive: Option[FiniteDuration] = None,
  // A factory for correlation Ids in case no Correlation Id is set in the message
  correlationId : () => Option[String] = () => None
) extends JmsSettings {

  def withConnectionTimeout(d : FiniteDuration): JmsProducerSettings = copy(connectionTimeout = d)

  def withSessionCount(count: Int): JmsProducerSettings = copy(sessionCount = count)

  def withQueue(name: String): JmsProducerSettings = copy(jmsDestination = Some(JmsQueue(name)))
  def withTopic(name: String): JmsProducerSettings = copy(jmsDestination = Some(JmsTopic(name)))

  def withPriority(p : Int) : JmsProducerSettings = copy(priority = p)

  def withTimeToLive(ttl: java.time.Duration): JmsProducerSettings = copy(timeToLive = Some(Duration.fromNanos(ttl.toNanos)))
  def withTimeToLive(ttl: FiniteDuration): JmsProducerSettings = copy(timeToLive = Some(ttl))
  def withTimeToLive(ttl: Long, unit: TimeUnit): JmsProducerSettings = copy(timeToLive = Some(Duration(ttl, unit)))
}

object JmsProducerSettings {

  def create(connectionFactory: ConnectionFactory) = JmsProducerSettings(connectionFactory)
}
