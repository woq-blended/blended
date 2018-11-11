package blended.jms.bridge

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{FlowShape, Materializer, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source, Zip}
import blended.jms.bridge.TrackTransaction.TrackTransaction
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.{FlowProcessor, StreamControllerConfig}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction._
import blended.streams.worklist.WorklistState
import blended.util.logging.Logger

import scala.util.Try

object TrackTransaction extends Enumeration {
  type TrackTransaction = Value
  val On, Off, FromMessage = Value
}

case class JmsStreamConfig(
  fromCf : IdAwareConnectionFactory,
  fromDest : JmsDestination,
  toCf : IdAwareConnectionFactory,
  toDest : Option[JmsDestination],
  listener : Int,
  selector : Option[String] = None,
  trackTransAction : TrackTransaction,
  registry : BridgeProviderRegistry,
  headerCfg : FlowHeaderConfig
)

class JmsStreamBuilder(
  cfg : JmsStreamConfig
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  // So that we find the stream in the logs
  private val inId = s"${cfg.fromCf.vendor}:${cfg.fromCf.provider}:${cfg.fromDest.asString}"
  private val outId = s"${cfg.toCf.vendor}:${cfg.toCf.provider}:${cfg.toDest.map(_.asString).getOrElse("out")}"
  private val streamId = s"${cfg.headerCfg.prefix}.bridge.JmsStream($inId->$outId)"

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

  private def transactionSink(internalCf : IdAwareConnectionFactory, eventDest : JmsDestination) :
    Flow[FlowEnvelope, FlowEnvelope, _] = {

    val g = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val settings = JmsProducerSettings(
        headerConfig = cfg.headerCfg,
        connectionFactory = internalCf,
        deliveryMode = JmsDeliveryMode.Persistent,
        jmsDestination = Some(eventDest)
      )

      val producer = b.add(jmsProducer(
        name = "event",
        settings = settings,
        autoAck = false,
        log = bridgeLogger
      ))

      val trackFilter = b.add(FlowProcessor.partition[FlowEnvelope] { env =>

        val doTrack : Boolean = cfg.trackTransAction match {
          case TrackTransaction.Off => false
          case TrackTransaction.On => true
          case TrackTransaction.FromMessage =>
            bridgeLogger.debug("Getting tracking mode from message")
            env.header[Boolean](cfg.headerCfg.prefix + cfg.headerCfg.headerTrack).getOrElse(false)
        }

        bridgeLogger.debug(s"Tracking for envelope [${env.id}] is [$doTrack]")

        doTrack
      })

      val switchOffTracking = b.add(Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
        bridgeLogger.debug(s"About to send envelope [$env]")
        env.withHeader(cfg.headerCfg.headerTrack, false).get
      })

      val merge = b.add(Merge[FlowEnvelope](2))

      trackFilter.out0 ~> switchOffTracking ~> producer ~> merge.in(0)
      trackFilter.out1 ~> merge.in(1)

      FlowShape(trackFilter.in, merge.out)
    }

    Flow.fromGraph(g)
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

    def startTransaction(env: FlowEnvelope) : FlowTransactionEvent = {
      FlowTransactionStarted(env.id, env.flowMessage.header)
    }

    def updateTransaction(env: FlowEnvelope) : FlowTransactionEvent = {

      env.exception match {
        case None =>
          val branch = env.header[String](cfg.headerCfg.headerBranch).getOrElse("default")
          FlowTransactionUpdate(env.id, env.flowMessage.header, WorklistState.Completed, branch)

        case Some(e) => FlowTransactionFailed(env.id, env.flowMessage.header,  Some(e.getMessage()))
      }
    }

    val g = FlowProcessor.fromFunction("logTransaction", bridgeLogger){ env =>
      Try {
        val event : FlowTransactionEvent = if ((cfg.toCf.vendor, cfg.toCf.provider) == internalId) {
          startTransaction(env)
        } else {
          updateTransaction(env)
        }

        bridgeLogger.debug(s"Generated transaction event [$event]")
        event

        FlowTransactionEvent.event2envelope(cfg.headerCfg)(event)
      }
    }

    Flow.fromGraph(g)
  }

  def transactionWiretap(internalCf : IdAwareConnectionFactory, eventDest : JmsDestination) : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
    val g = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val split = b.add(Broadcast[FlowEnvelope](2))
      val sink = b.add(transactionSink(internalCf, eventDest))
      val log = b.add(Flow.fromFunction[FlowEnvelope, FlowEnvelope]{ env =>
        bridgeLogger.debug(s"Transaction Wiretap [$env]")
        env
      })
      val zip = b.add(Zip[FlowEnvelope, FlowEnvelope]())
      val select = b.add(Flow.fromFunction[(FlowEnvelope, FlowEnvelope), FlowEnvelope]{ _._2 })

      split.out(1) ~> zip.in1
      split.out(0) ~> log ~> sink ~> zip.in0

      zip.out ~> select

      FlowShape(split.in, select.out)
    }

    Flow.fromGraph(g)
  }

  // We will stream from the inbound destination to the inbound destination of the internal provider
  private val stream : Source[FlowEnvelope, NotUsed] = {

    RestartableJmsSource(
      name = streamId, settings = srcSettings, log = bridgeLogger
    )
    .via(transactionFlow())
    .via(transactionWiretap(internalCf.get, internalProvider.get.transactions))
    .via(jmsProducer(name = streamId, settings = toSettings, autoAck = true, log = bridgeLogger))
  }

  // The stream will be handled by an actor which that can be used to shutdown the stream
  // and will restart the stream with a backoff strategy on failure
  val streamCfg : StreamControllerConfig = StreamControllerConfig(
    name = streamId, source = stream
  )
}
