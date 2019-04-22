package blended.streams.jms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, Source}
import akka.stream.{FlowShape, Graph, Materializer}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.message.FlowEnvelope
import blended.streams.processor.AckProcessor
import blended.streams.transaction.FlowHeaderConfig
import blended.streams.{FlowProcessor, StreamController, StreamControllerConfig}
import blended.util.logging.Logger
import akka.stream.scaladsl.GraphDSL.Implicits._
import javax.jms.Session

import scala.concurrent.duration._
import scala.util.Try

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

class JmsRetryProcessor(name : String, retryCfg : JmsRetryConfig)(
  implicit system : ActorSystem, materializer : Materializer
) extends JmsStreamSupport {

  private[this] val retryLog : Logger = Logger(retryCfg.headerCfg.prefix + ".retry." + retryCfg.retryDestName)
  private[this] val log : Logger = Logger[JmsRetryProcessor]

  class RetryDestinationResolver(
    override val headerConfig : FlowHeaderConfig,
    override val settings : JmsProducerSettings
  ) extends FlowHeaderConfigAware with JmsEnvelopeHeader {

    override def sendParameter(session: Session, env: FlowEnvelope): Try[JmsSendParameter] = Try {

      val dest : JmsDestination = env.exception match {
        case None =>
          JmsDestination.create(env.headerWithDefault[String](headerConfig.headerRetryDestination, retryCfg.failedDestName)).get
        case Some(e) =>
          JmsDestination.create(retryCfg.failedDestName).get
      }

      JmsSendParameter(
        message = createJmsMessage(session, env).get,
        destination = dest,
        deliveryMode = JmsDeliveryMode.Persistent,
        priority = settings.priority,
        ttl = settings.timeToLive
      )
    }
  }

  private[this] val retrySource : Source[FlowEnvelope, NotUsed] = {
    val settings = JMSConsumerSettings(
      log = retryLog,
      connectionFactory = retryCfg.cf,
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge,
      jmsDestination = Some(JmsDestination.create(retryCfg.retryDestName).get)
    )

    jmsConsumer(
      name = settings.jmsDestination.get.asString,
      settings = settings,
      headerConfig = retryCfg.headerCfg,
      minMessageDelay = Some(retryCfg.retryInterval)
    )
  }

  private val routeRetry : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {
    val router = new JmsRetryRouter(retryCfg, retryLog)
    FlowProcessor.fromFunction("route", retryLog)(router.resolve)
  }

  private val retrySend : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
    val producerSettings : JmsProducerSettings = JmsProducerSettings(
      log = retryLog,
      connectionFactory = retryCfg.cf,
      destinationResolver = s => new RetryDestinationResolver(retryCfg.headerCfg, s),
      deliveryMode = JmsDeliveryMode.Persistent,
      timeToLive = None,
      clearPreviousException = true
    )

    jmsProducer(
      name = name + "routeSend",
      settings = producerSettings,
      autoAck = false,
    )
  }

  def retryGraph : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit b =>

      // determine the retry routing parameters from the message
      val route = b.add(routeRetry)

      val routeSend = b.add(retrySend)

      // After determining the retry parameters we send the envelope to either the Retry Destination
      // or the ReryFailed destination
      route.out ~> routeSend.in

      // Acknowledge / Deny the result of the overall retry flow
      val ack = b.add(new AckProcessor(name + ".ack").flow)
      routeSend.out ~> ack.in

      // Finally we hook up the dangling endpoints of the flow
      new FlowShape[FlowEnvelope, FlowEnvelope](
        route.in,
        ack.out
      )
    }
  }

  def start() : Unit = {

    log.info(s"Starting Jms Retry processor for [${retryCfg.retryDestName}] with retry interval [${retryCfg.retryInterval}]")

    val src : Source[FlowEnvelope, NotUsed] = retrySource
      .via(retryGraph)

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
