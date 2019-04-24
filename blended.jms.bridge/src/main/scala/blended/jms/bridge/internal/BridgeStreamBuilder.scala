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
import blended.streams.processor.{HeaderProcessorConfig, HeaderTransformProcessor}
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
  inbound : Boolean,
  fromCf : IdAwareConnectionFactory,
  fromDest : JmsDestination,
  toCf : IdAwareConnectionFactory,
  toDest : Option[JmsDestination],
  listener : Int,
  selector : Option[String] = None,
  trackTransaction : TrackTransaction,
  registry : BridgeProviderRegistry,
  headerCfg : FlowHeaderConfig,
  subscriberName : Option[String],
  header : List[HeaderProcessorConfig],
  idSvc : Option[ContainerIdentifierService] = None,
  rawConfig : Config,
  sessionRecreateTimeout : FiniteDuration
)

class BridgeStreamBuilder(
  cfg : BridgeStreamConfig
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  // So that we find the stream in the logs
  private val inId = s"${cfg.fromCf.vendor}:${cfg.fromCf.provider}:${cfg.fromDest.asString}"
  private val outId = s"${cfg.toCf.vendor}:${cfg.toCf.provider}:${cfg.toDest.map(_.asString).getOrElse("out")}"
  private val streamId = s"${cfg.headerCfg.prefix}.bridge.JmsStream($inId->$outId)"
  private val bridgeLogger = Logger(streamId)

  // configure the consumer
  private val srcSettings = JMSConsumerSettings(bridgeLogger, cfg.fromCf)
    .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)
    .withDestination(Some(cfg.fromDest))
    .withSessionCount(cfg.listener)
    .withSelector(cfg.selector)
    .withSubScriberName(cfg.subscriberName)

  // How we resolve the target destination
  private val destResolver = cfg.toDest match {
    case Some(_) => s : JmsProducerSettings => new SettingsDestinationResolver(s)
    case None => s : JmsProducerSettings => new MessageDestinationResolver(
      headerConfig = cfg.headerCfg,
      settings = s
    )
  }

  private val toSettings = JmsProducerSettings(
    log = bridgeLogger,
    connectionFactory = cfg.toCf
  )
    .withDestination(cfg.toDest)
    .withDestinationResolver(destResolver)
    .withDeliveryMode(JmsDeliveryMode.Persistent)

  private val internalProvider : Try[BridgeProviderConfig] = cfg.registry.internalProvider
  private val internalId = (internalProvider.get.vendor, internalProvider.get.provider)

  private val internalCf : Try[IdAwareConnectionFactory] = Try {

    if ((cfg.fromCf.vendor, cfg.fromCf.provider) == internalId) {
      cfg.fromCf
    } else if ((cfg.toCf.vendor, cfg.toCf.provider) == internalId) {
      cfg.toCf
    } else {
      throw new InvalidBridgeConfigurationException("One leg of the JMS bridge must be internal")
    }
  }

  // Decide whether a tracking event should be generated for this bridge step
  private[bridge] val trackFilter = FlowProcessor.partition[FlowEnvelope] { env =>

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

  private val stream : Source[FlowEnvelope, NotUsed] = {

    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val trackSplit = b.add(trackFilter)
      val mergeResult = b.add(Merge[FlowEnvelope](2))

      val wiretap : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
        new TransactionWiretap(
          cf = internalCf.get,
          eventDest = internalProvider.get.transactions,
          headerCfg = cfg.headerCfg,
          inbound = cfg.inbound,
          trackSource = streamId,
          log = bridgeLogger
        ).flow()


      trackSplit.out0 ~> wiretap ~> mergeResult.in(0)
      trackSplit.out1 ~> mergeResult.in(1)

      FlowShape(trackSplit.in, mergeResult.out)
    }

    val src : Source[FlowEnvelope, NotUsed] =
      Source.fromGraph(new JmsAckSourceStage(
        name = streamId + "-source",
        settings = srcSettings,
        headerConfig = cfg.headerCfg
      ))

    val jmsSource : Source[FlowEnvelope, NotUsed] = if (cfg.inbound && cfg.header.nonEmpty) {

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

    jmsSource
      .via(Flow.fromGraph(g))
      .via(jmsProducer(name = streamId + "-sink", settings = toSettings, autoAck = true))
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
