package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source}
import akka.stream.{FanOutShape2, FlowShape, Graph, Materializer}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.internal.TrackTransaction.TrackTransaction
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.processor.{AckProcessor, HeaderProcessorConfig, HeaderTransformProcessor}
import blended.streams.transaction._
import blended.streams.{BlendedStreamsConfig, FlowProcessor}
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class InvalidBridgeConfigurationException(msg : String) extends Exception(msg)

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
  // the minimum timespan after which a new session will be created after closing a session
  // upon an exception
  sessionRecreateTimeout : FiniteDuration
)

class BridgeStreamBuilder(
  bridgeCfg : BridgeStreamConfig,
  streamsConfig : BlendedStreamsConfig
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  // So that we find the stream in the logs
  protected val inId = s"${bridgeCfg.fromCf.vendor}:${bridgeCfg.fromCf.provider}:${bridgeCfg.fromDest.asString}"
  protected val outId = s"${bridgeCfg.toCf.vendor}:${bridgeCfg.toCf.provider}:${bridgeCfg.toDest.map(_.asString).getOrElse("out")}"
  val streamId = s"${bridgeCfg.headerCfg.prefix}.bridge.JmsStream($inId->$outId)"
  protected val bridgeLogger = Logger(streamId)

  protected val transShard : Option[String] = streamsConfig.transactionShard

  protected val toSettings : IdAwareConnectionFactory => Option[JmsDestination] => JmsProducerSettings = cf => dest => {
    val resolver : JmsProducerSettings => JmsDestinationResolver = dest match {
      case Some(_) => s : JmsProducerSettings => new SettingsDestinationResolver(s)
      case None => s : JmsProducerSettings => new MessageDestinationResolver(
        settings = s
      )
    }

    JmsProducerSettings(
      log = bridgeLogger,
      connectionFactory = cf,
      headerCfg = bridgeCfg.headerCfg
    )
      .withDestination(dest)
      .withDestinationResolver(resolver)
      .withDeliveryMode(JmsDeliveryMode.Persistent)
  }

  protected val internalProvider : Try[BridgeProviderConfig] = bridgeCfg.registry.internalProvider
  protected val internalId : (String, String) = (internalProvider.get.vendor, internalProvider.get.provider)
  protected val retryDest : Option[JmsDestination] = internalProvider.get.retry

  protected val (isInbound, internalCf) : (Boolean, Try[IdAwareConnectionFactory]) = {

    if ((bridgeCfg.fromCf.vendor, bridgeCfg.fromCf.provider) == internalId) {
      (false, Success(bridgeCfg.fromCf))
    } else if ((bridgeCfg.toCf.vendor, bridgeCfg.toCf.provider) == internalId) {
      (true, Success(bridgeCfg.toCf))
    } else {
      (true, Failure(new InvalidBridgeConfigurationException("One leg of the JMS bridge must be internal")))
    }
  }

  // The jmsSource provides the inbound stream of FlowEnvelopes that need to be passed onwards
  // to the target JMS destination
  protected def jmsSource : Source[FlowEnvelope, NotUsed] = {

    // configure the consumer
    val srcSettings = JMSConsumerSettings(log = bridgeLogger, connectionFactory = bridgeCfg.fromCf, headerCfg = bridgeCfg.headerCfg)
      .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)
      .withDestination(Some(bridgeCfg.fromDest))

      .withSessionCount(bridgeCfg.listener)
      .withSelector(bridgeCfg.selector)
      .withSubScriberName(bridgeCfg.subscriberName)

    val src : Source[FlowEnvelope, NotUsed] = {
      val result : Source[FlowEnvelope, NotUsed] = Source.fromGraph(new JmsConsumerStage(
        name = streamId + "-source",
        consumerSettings = srcSettings
      ))

      // set the transaction from a the system property blended.streams.transactionShard
      // Maintaining the transaction shard in the message will ensure that all transaction events
      // with the same event id will end up in the same transaction destination.
      transShard match {
        case None => result
        case Some(shard) => result.via(Flow.fromFunction[FlowEnvelope, FlowEnvelope]{ env =>
          env.withHeader(bridgeCfg.headerCfg.headerTransShard, shard, false).get
        })
      }
    }

    // If we need to transform additional headers on the inbound leg
    if (bridgeCfg.inbound && bridgeCfg.header.nonEmpty) {

      bridgeLogger.info(s"Creating Stream with header configs [${bridgeCfg.header}]")

      val header : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = HeaderTransformProcessor(
        name = streamId + "-header",
        log = bridgeLogger,
        rules = bridgeCfg.header,
        idSvc = bridgeCfg.idSvc
      ).flow(bridgeLogger)

      src.via(header)
    } else {
      bridgeLogger.info(s"Creating Stream without additional header configs")
      src
    }
  }

  // The jms producer for forwarding the messages to the target destination
  protected def jmsSend : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
    jmsProducer(name = streamId + "-sink", settings = toSettings(bridgeCfg.toCf)(bridgeCfg.toDest), autoAck = false)
  }

  // The producer to send the current envelope to the retry queue in case of an error
  // We only forward the envelope to the retry Queue if the retry destination is set
  // AND the bridge direction is outbound
  protected def jmsRetry : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val skipRetry : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
      Flow.fromGraph(FlowProcessor.log(LogLevel.Debug, bridgeLogger, "Skipping retry"))

    retryDest match {
      case None =>
        bridgeLogger.debug(s"No retry destination set, retry mechanism will be disabled")
        skipRetry

      case Some(d) => if (isInbound) {
        bridgeLogger.debug(s"Retry mechanism will be disabled for inbound bridge direction")
        skipRetry
      } else {
        logEnvelope(s"Forwarding to retry [$d]")
          .via(
            Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
              env.withHeader(bridgeCfg.headerCfg.headerRetryDestination, JmsDestination.asString(bridgeCfg.fromDest)).get
            }
          )
          .via(
            jmsProducer(
              name = streamId + "-retry",
              settings = toSettings(bridgeCfg.fromCf)(Some(d)).copy(clearPreviousException = true),
              autoAck = false
            )
          )
      }
    }
  }

  // Decide whether a tracking event should be generated for this bridge step
  protected[bridge] val trackFilter : Graph[FanOutShape2[FlowEnvelope, FlowEnvelope, FlowEnvelope], NotUsed] = FlowProcessor.partition[FlowEnvelope] { env =>

    val doTrack : Boolean = bridgeCfg.trackTransaction match {
      case TrackTransaction.Off => false
      case TrackTransaction.On  => true
      case TrackTransaction.FromMessage =>
        bridgeLogger.trace(s"Getting tracking mode from message property [${bridgeCfg.headerCfg.headerTrack}]")
        val msgTrack = env.header[Boolean](bridgeCfg.headerCfg.headerTrack)
        msgTrack.getOrElse(false)
    }

    bridgeLogger.debug(s"Tracking for envelope [${env.id}] is [$doTrack]")

    doTrack
  }

  protected def sendTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = new TransactionWiretap(
    cf = internalCf.get,
    eventDest = internalProvider.get.transactions,
    headerCfg = bridgeCfg.headerCfg,
    inbound = bridgeCfg.inbound,
    trackSource = streamId,
    log = bridgeLogger
  ).flow()

  // flow to generate a transaction event from the current envelope and send it to the
  // JMS transaction endpoint
  protected def transactionFlow : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>

      import GraphDSL.Implicits._

      val doLog = b.add(logEnvelope("Before Tracking"))

      // First we decide wether we need to track the transaction
      val doTrack = b.add(trackFilter)
      doLog.out ~> doTrack.in

      // The actual send of the transaction
      val send = sendTransaction

      val merge = b.add(Merge[FlowEnvelope](2))

      val sendError = b.add(FlowProcessor.partition[FlowEnvelope](_.exception.isEmpty))
      val mergeError = b.add(Merge[FlowEnvelope](2))
      val retry = b.add(jmsRetry)

      doTrack.out0 ~> send ~> sendError.in

      sendError.out0 ~> mergeError.in(0)
      sendError.out1 ~> retry ~> mergeError.in(1)

      mergeError.out ~> merge.in(0)
      doTrack.out1 ~> merge.in(1)

      FlowShape(doLog.in, merge.out)
    }

    Flow.fromGraph(g)
  }

  protected def logEnvelope(msg : String) : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
    Flow.fromGraph(FlowProcessor.log(LogLevel.Debug, bridgeLogger, msg))

  val stream : Source[FlowEnvelope, NotUsed] = {

    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // We will forward the message to the target destination
      val forward = b.add(jmsSend.via(logEnvelope("After Send")))

      // Then we have a path where the send is successfull and one where the send has failed
      val sendError = b.add(FlowProcessor.partition[FlowEnvelope] {
        _.exception.isEmpty
      })
      forward ~> sendError.in

      val merge = b.add(Merge[FlowEnvelope](2))

      // In case the send was success full we will track the transaction if required and then
      val transaction = b.add(transactionFlow)

      sendError.out0 ~> transaction ~> merge.in(0)

      // In case of an exception we will
      // - pass the message to the retry queue if the direction is outbound && the retry is enabled
      //   if the send to the retry fails, the envelope will simply be rejected
      // - simply deny the message if the direction is inbound or the retry is disabled

      val retry = b.add(jmsRetry)
      sendError.out1 ~> retry ~> merge.in(1)

      // Finally, we acknowledge or reject the envelope, depending whether we have encountered an exception
      val ack = b.add(new AckProcessor(streamId + "-ack").flow)

      merge.out ~> ack.in

      FlowShape(forward.in, ack.out)
    }

    jmsSource.via(g)
  }

  bridgeLogger.info(s"Starting bridge stream with config [inbound=${bridgeCfg.inbound},trackTransaction=${bridgeCfg.trackTransaction}]")
}
