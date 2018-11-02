package blended.jms.bridge

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{FlowShape, Materializer}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.{FlowProcessor, StreamControllerConfig}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction._
import blended.streams.worklist.WorklistState
import blended.util.logging.Logger

import scala.util.Try

case class JmsStreamConfig(
  fromCf : IdAwareConnectionFactory,
  fromDest : JmsDestination,
  toCf : IdAwareConnectionFactory,
  toDest : Option[JmsDestination],
  listener : Int,
  selector : Option[String] = None,
  trackTransAction : Boolean,
  registry : BridgeProviderRegistry,
  headerCfg : FlowHeaderConfig
)

class JmsStreamBuilder(
  cfg : JmsStreamConfig
)(implicit system: ActorSystem, materializer: Materializer) {

  // So that we find the stream in the logs
  private val inId = s"${cfg.fromCf.vendor}:${cfg.fromCf.provider}:${cfg.fromDest.asString}"
  private val outId = s"${cfg.toCf.vendor}:${cfg.toCf.provider}:${cfg.toDest.map(_.asString).getOrElse("out")}"
  private val streamId = s"JmsStream($inId->$outId)"

  // configure the consumer
  private val srcSettings = JMSConsumerSettings(cfg.fromCf, cfg.headerCfg)
    .withDestination(Some(cfg.fromDest))
    .withSessionCount(cfg.listener)
    .withSelector(cfg.selector)

  // How we resolve the target destination
  private val destResolver = cfg.toDest match {
    case Some(_) => s : JmsProducerSettings => new SettingsDestinationResolver(s)
    case None => s : JmsProducerSettings => new MessageDestinationResolver(s)
  }

  private val toSettings = JmsProducerSettings(
    connectionFactory = cfg.toCf,
    headerConfig = cfg.headerCfg
  )
    .withDestination(cfg.toDest)
    .withDestinationResolver(destResolver)
    .withDeliveryMode(JmsDeliveryMode.Persistent)

  private val bridgeLogger = Logger(streamId)

  private def transactionSink(internalCf : IdAwareConnectionFactory, eventDest : JmsDestination) : Sink[FlowEnvelope, _] = {
    if (cfg.trackTransAction) {
      val settings = JmsProducerSettings(
        headerConfig = cfg.headerCfg,
        connectionFactory = internalCf,
        deliveryMode = JmsDeliveryMode.Persistent,
        jmsDestination = Some(eventDest)
      )

      val producer = JmsProducerSupport.jmsProducer(
        name = "event",
        settings = settings,
        autoAck = false,
        log = Some(bridgeLogger)
      )

      transactionFlow().via(producer).to(Sink.ignore)
    } else {
      Sink.ignore
    }
  }

  private val internalProvider : Try[BridgeProviderConfig] = cfg.registry.internalProvider
  val internalId = (internalProvider.get.vendor, internalProvider.get.provider)

  private val internalCf : Try[IdAwareConnectionFactory] = Try {

    if ((cfg.fromCf.vendor, cfg.fromCf.provider) == internalId) {
      cfg.fromCf
    } else if ((cfg.toCf.vendor, cfg.toCf.provider) == internalId) {
      cfg.toCf
    } else {
      throw new Exception("One leg of the JMS bridge must be internal")
    }
  }

  private def transactionFlow() : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    def startTransaction(env: FlowEnvelope) : Try[FlowEnvelope] = Try {
      val event = FlowTransactionStarted(env.id, env.flowMessage.header)
      bridgeLogger.debug(s"Generating transaction event [$event]")
      FlowTransactionEvent.event2envelope(cfg.headerCfg)(event)
    }

    def updateTransaction(env: FlowEnvelope) : Try[FlowEnvelope] = Try {

      env.exception match {
        case None =>
          val branch = env.header[String](cfg.headerCfg.headerBranch).getOrElse("default")
          val event = FlowTransactionUpdate(env.id, WorklistState.Completed, branch)
          bridgeLogger.debug(s"Generating transaction event [$event]")
          FlowTransactionEvent.event2envelope(cfg.headerCfg)(event)

        case Some(e) => FlowTransactionEvent.event2envelope(cfg.headerCfg)(
          FlowTransactionFailed(env.id, Some(e.getMessage()))
        )
      }
    }

    val g = FlowProcessor.fromFunction("logTransaction", bridgeLogger){ env =>
      Try {
        if ((cfg.toCf.vendor, cfg.toCf.provider) == internalId) {
          startTransaction(env).get
        } else {
          updateTransaction(env).get
        }
      }
    }

    Flow.fromGraph(g)
  }

  def transactionWiretap(internalCf : IdAwareConnectionFactory, eventDest : JmsDestination) : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
    val g = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val split = b.add(Broadcast[FlowEnvelope](2))
      val sink = b.add(transactionSink(internalCf, eventDest))

      split.out(0) ~> sink

      FlowShape(split.in, split.out(1))
    }

    Flow.fromGraph(g)
  }

  // We will stream from the inbound destination to the inbound destination of the internal provider
  private val stream : Source[FlowEnvelope, NotUsed] = {

    RestartableJmsSource(
      name = streamId, settings = srcSettings, requiresAck = true, log = bridgeLogger
    )
    .via(transactionWiretap(internalCf.get, internalProvider.get.eventDestination))
    .via(JmsProducerSupport.jmsProducer(name = streamId, settings = toSettings, autoAck = true, log = Some(bridgeLogger)))
  }

  // The stream will be handled by an actor which that can be used to shutdown the stream
  // and will restart the stream with a backoff strategy on failure
  val streamCfg : StreamControllerConfig = StreamControllerConfig(
    name = streamId, source = stream
  )
}
