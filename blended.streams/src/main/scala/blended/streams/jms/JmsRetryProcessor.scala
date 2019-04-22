package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.{StreamController, StreamControllerConfig}
import blended.streams.message.FlowEnvelope
import blended.streams.processor.AckProcessor
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger

import scala.concurrent.duration._

case class JmsRetryConfig(
  cf : IdAwareConnectionFactory,
  headerCfg : FlowHeaderConfig,
  retryDestName : String,
  failedDestName : String,
  retryInterval : FiniteDuration,
  maxRetries : Long = -1,
  retryTimeout : FiniteDuration = 1.day
)

object JmsRetryProcessor {
  def apply(name : String, cfg : JmsRetryConfig)(
    implicit system : ActorSystem, materializer : Materializer
  ): JmsRetryProcessor = new JmsRetryProcessor(name, cfg)
}

class JmsRetryProcessor(name : String, cfg : JmsRetryConfig)(
  implicit system : ActorSystem, materializer : Materializer
) extends JmsStreamSupport {

  private[this] val retryLog : Logger = Logger(cfg.headerCfg.prefix + ".retry." + cfg.retryDestName)
  private[this] val log : Logger = Logger[JmsRetryProcessor]

  private[this] val retrySource : Source[FlowEnvelope, NotUsed] = {
    val settings = JMSConsumerSettings(
      log = retryLog,
      connectionFactory = cfg.cf,
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge,
      jmsDestination = Some(JmsDestination.create(cfg.retryDestName).get)
    )

    jmsConsumer(
      name = settings.jmsDestination.get.asString,
      settings = settings,
      headerConfig = cfg.headerCfg,
      minMessageDelay = Some(cfg.retryInterval)
    )
  }

  def start() : Unit = {

    log.info(s"Starting Jms Retry processor for [${cfg.retryDestName}] with retry interval [${cfg.retryInterval}]")

    val src : Source[FlowEnvelope, NotUsed] = retrySource
      .via(new AckProcessor(name + ".ack").flow)

    // TODO: Load from config
    val streamCfg : StreamControllerConfig = StreamControllerConfig(
      name = name,
      source = src,
      minDelay = 10.seconds,
      maxDelay = 3.minutes,
      exponential = true,
      onFailureOnly = true,
      random = 0.2
    )

    system.actorOf(StreamController.props(streamCfg))
  }
}
