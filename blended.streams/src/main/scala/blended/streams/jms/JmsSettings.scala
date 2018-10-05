package blended.streams.jms

import blended.jms.utils.{JmsDestination, JmsQueue, JmsTopic}
import javax.jms.{ConnectionFactory, Session}

import scala.concurrent.duration._
import scala.concurrent.duration.Duration

final class AcknowledgeMode(val mode: Int)

object AcknowledgeMode {
  val AutoAcknowledge: AcknowledgeMode = new AcknowledgeMode(Session.AUTO_ACKNOWLEDGE)
  val ClientAcknowledge: AcknowledgeMode = new AcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
  val DupsOkAcknowledge: AcknowledgeMode = new AcknowledgeMode(Session.DUPS_OK_ACKNOWLEDGE)
  val SessionTransacted: AcknowledgeMode = new AcknowledgeMode(Session.SESSION_TRANSACTED)
}

sealed trait JmsSettings {
  def connectionFactory: ConnectionFactory
  val connectionTimeout: FiniteDuration
  val jmsDestination : Option[JmsDestination]
  val sessionCount : Int
}

final case class JMSConsumerSettings(
  connectionFactory: ConnectionFactory,
  connectionTimeout : FiniteDuration,
  jmsDestination: Option[JmsDestination],
  acknowledgeMode: AcknowledgeMode = AcknowledgeMode.AutoAcknowledge,
  sessionCount: Int = 1,
  bufferSize: Int = 100,
  selector: Option[String] = None,
  ackTimeout: Duration = 1.second,
  durableName: Option[String] = None
) extends JmsSettings {

  def withAcknowledgeMode(m: AcknowledgeMode): JMSConsumerSettings = copy(acknowledgeMode = m)
  def withSessionCount(c : Int): JMSConsumerSettings = copy(sessionCount = c)

  def withBufferSize(s : Int): JMSConsumerSettings = copy(bufferSize = s)

  def noSelector(): JMSConsumerSettings = copy(selector = None)
  def withSelector(s : String): JMSConsumerSettings = copy(selector = Some(s))

  def withAckTimeout(d : Duration): JMSConsumerSettings = copy(ackTimeout = d)

  def noDurableName(): JMSConsumerSettings = copy(durableName = None)
  def withDurableName(s: String): JMSConsumerSettings = copy(durableName = Some(s))
}

object JMSConsumerSettings {

  def apply(cf: ConnectionFactory, connectionTimeout: Duration, destination: JmsDestination) : JMSConsumerSettings =
    JMSConsumerSettings(cf, connectionTimeout, destination)
}

final case class JmsProducerSettings(
  connectionFactory: ConnectionFactory,
  connectionTimeout : FiniteDuration,
  jmsDestination: Option[JmsDestination] = None,
  sessionCount: Int = 1,
  timeToLive: Option[Duration] = None
) extends JmsSettings {

  def withSessionCount(count: Int): JmsProducerSettings = copy(sessionCount = count)

  def withQueue(name: String): JmsProducerSettings = copy(jmsDestination = Some(JmsQueue(name)))
  def withTopic(name: String): JmsProducerSettings = copy(jmsDestination = Some(JmsTopic(name)))

  def withTimeToLive(ttl: java.time.Duration): JmsProducerSettings = copy(timeToLive = Some(Duration.fromNanos(ttl.toNanos)))
  def withTimeToLive(ttl: Duration): JmsProducerSettings = copy(timeToLive = Some(ttl))
  def withTimeToLive(ttl: Long, unit: TimeUnit): JmsProducerSettings = copy(timeToLive = Some(Duration(ttl, unit)))
}

object JmsProducerSettings {
  def create(connectionFactory: ConnectionFactory, connectionTimeout : FiniteDuration) =
    JmsProducerSettings(connectionFactory, connectionTimeout)
}
