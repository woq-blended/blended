package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source}
import akka.stream.{FlowShape, Graph, Materializer}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.internal.TrackTransaction.TrackTransaction
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, HeaderProcessorConfig, HeaderTransformProcessor}
import blended.streams.transaction._
import blended.streams.{FlowProcessor, StreamControllerConfig}
import blended.util.logging.Logger
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class InvalidBridgeConfigurationException(msg: String) extends Exception(msg)

object TrackTransaction extends Enumeration {
  type TrackTransaction = Value
  val On, Off, FromMessage = Value
}

case class BridgeStreamConfig(
  // whether the BridgeStream is container inbound or container outbound
  inbound : Boolean,
  // the JMS connection factory to be used to consume messages
  fromCf : IdAwareConnectionFactory,
  // Jms Destination to consume messages from
  fromDest : JmsDestination,
  // the JMS connection factory to forward the messages to
  toCf : IdAwareConnectionFactory,
  // optional destination to forward the messages to. If None, the target destination
  // must be set in the message
  toDest : Option[JmsDestination],
  // the number of consumers consuming in parallel from the source destination
  listener : Int,
  // An optional selector to consume the messages
  selector : Option[String] = None,
  // Whether to track transactions, can be set to No / Yes / fromMessage
  trackTransaction : TrackTransaction,
  // a Bridge provider registry which contains all currently available JMS connection
  // factories within the container
  registry : BridgeProviderRegistry,
  // The header confirguration of the container (effectively provides headernames with
  // customized prefixes)
  headerCfg : FlowHeaderConfig,
  // A subscriber name that must be used when the source destination is a topic
  subscriberName : Option[String],
  // Optional list of headers to be set after consuming messages from the source
  header : List[HeaderProcessorConfig],
  // A reference to an ContainerIdentifierService that must be used to resolve header
  // expressions
  idSvc : Option[ContainerIdentifierService] = None,
  // The raw typesafe config object for the bridge configuration
  rawConfig : Config,
  // the minimum timespan after which a new session will be created after closing a session
  // upon an exception
  sessionRecreateTimeout : FiniteDuration
)

class BridgeStreamBuilder(
  cfg : BridgeStreamConfig
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  // So that we find the stream in the logs
  protected val inId = s"${cfg.fromCf.vendor}:${cfg.fromCf.provider}:${cfg.fromDest.asString}"
  protected val outId = s"${cfg.toCf.vendor}:${cfg.toCf.provider}:${cfg.toDest.map(_.asString).getOrElse("out")}"
  protected val streamId = s"${cfg.headerCfg.prefix}.bridge.JmsStream($inId->$outId)"
  protected val bridgeLogger = Logger(streamId)

  // How we resolve the target destination
  protected val destResolver : JmsProducerSettings => JmsDestinationResolver = cfg.toDest match {
    case Some(_) => s : JmsProducerSettings => new SettingsDestinationResolver(s)
    case None => s : JmsProducerSettings => new MessageDestinationResolver(
      headerConfig = cfg.headerCfg,
      settings = s
    )
  }

  protected val toSettings : JmsProducerSettings = JmsProducerSettings(
    log = bridgeLogger,
    connectionFactory = cfg.toCf
  )
    .withDestination(cfg.toDest)
    .withDestinationResolver(destResolver)
    .withDeliveryMode(JmsDeliveryMode.Persistent)

  protected val internalProvider : Try[BridgeProviderConfig] = cfg.registry.internalProvider
  protected val internalId : (String, String) = (internalProvider.get.vendor, internalProvider.get.provider)
  protected val retryDest : Option[JmsDestination] = internalProvider.get.retry

  protected val internalCf : Try[IdAwareConnectionFactory] = Try {

    if ((cfg.fromCf.vendor, cfg.fromCf.provider) == internalId) {
      cfg.fromCf
    } else if ((cfg.toCf.vendor, cfg.toCf.provider) == internalId) {
      cfg.toCf
    } else {
      throw new InvalidBridgeConfigurationException("One leg of the JMS bridge must be internal")
    }
  }

  // The jmsSource provides the inbound stream of FlowEnvelopes that need to be passed onwards
  // to the target JMS destination
  protected def jmsSource : Source[FlowEnvelope, NotUsed] = {

    // configure the consumer
    val srcSettings = JMSConsumerSettings(bridgeLogger, cfg.fromCf)
      .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)
      .withDestination(Some(cfg.fromDest))
      .withSessionCount(cfg.listener)
      .withSelector(cfg.selector)
      .withSubScriberName(cfg.subscriberName)

    val src : Source[FlowEnvelope, NotUsed] =
      Source.fromGraph(new JmsAckSourceStage(
        name = streamId + "-source",
        settings = srcSettings,
        headerConfig = cfg.headerCfg
      ))

    // If we need to transform additional headers on the inbound leg
    if (cfg.inbound && cfg.header.nonEmpty) {

      bridgeLogger.info(s"Creating Stream with header configs [${cfg.header}]")

      val header : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = HeaderTransformProcessor(
        name = streamId + "-header",
        log = bridgeLogger,
        rules = cfg.header,
        idSvc = cfg.idSvc
      ).flow(bridgeLogger)

      src.via(header)
    } else {
      bridgeLogger.info(s"Creating Stream without additional header configs")
      src
    }
  }

  // The jms producer for forwarding the messages to the target destination
  protected def jmsSend : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
    jmsProducer(name = streamId + "-sink", settings = toSettings, autoAck = false)

  // The producer to send the current envelope to the retry queue in case of an error
  protected def jmsRetry : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = retryDest match {
    case None => Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env => env }
    case Some(d) => jmsProducer(
      name = streamId + "-retry",
      settings = toSettings.copy(
        jmsDestination = Some(d),
        clearPreviousException = true
      ),
      autoAck = false
    )
  }

  // to acknowledge the message once everything is done
  protected def acknowledge : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
    new AckProcessor(streamId + "-ack").flow

  // flow to generate a transaction event from the current envelope and send it to the
  // JMS transaction endpoint
  protected def sendTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
    new TransactionWiretap(
      cf = internalCf.get,
      eventDest = internalProvider.get.transactions,
      headerCfg = cfg.headerCfg,
      inbound = cfg.inbound,
      trackSource = streamId,
      log = bridgeLogger
    ).flow()

  // Decide whether a tracking event should be generated for this bridge step
  protected[bridge] val trackFilter = FlowProcessor.partition[FlowEnvelope] { env =>

    val doTrack : Boolean = cfg.trackTransaction match {
      case TrackTransaction.Off => false
      case TrackTransaction.On => true
      case TrackTransaction.FromMessage =>
        bridgeLogger.trace(s"Getting tracking mode from message property [${cfg.headerCfg.headerTrack}]")
        val msgTrack = env.header[Boolean](cfg.headerCfg.headerTrack)
        msgTrack.getOrElse(false)
    }

    bridgeLogger.debug(s"Tracking for envelope [${env.id}] is [$doTrack]")

    doTrack
  }

  protected val stream : Source[FlowEnvelope, NotUsed] = {

    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val trackSplit = b.add(trackFilter)
      val mergeResult = b.add(Merge[FlowEnvelope](2))

      trackSplit.out0 ~> sendTransaction ~> mergeResult.in(0)
      trackSplit.out1 ~> mergeResult.in(1)

      FlowShape(trackSplit.in, mergeResult.out)
    }

    jmsSource
      .via(Flow.fromGraph(g))
      .via(jmsSend)
      .via(acknowledge)
  }

  bridgeLogger.info(s"Starting bridge stream with config [inbound=${cfg.inbound},trackTransaction=${cfg.trackTransaction}]")
  // The stream will be handled by an actor which that can be used to shutdown the stream
  // and will restart the stream with a backoff strategy on failure
  val streamCfg : StreamControllerConfig = StreamControllerConfig.fromConfig(cfg.rawConfig).get
    .copy(
      name = streamId,
      source = stream
    )
}
