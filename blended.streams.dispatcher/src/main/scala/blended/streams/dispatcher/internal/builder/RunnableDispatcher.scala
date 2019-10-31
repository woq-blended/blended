package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionManager, TransactionDestinationResolver, TransactionWiretap}
import blended.streams.{BlendedStreamsConfig, StreamController, StreamControllerConfig}
import blended.util.logging.{LogLevel, Logger}

import scala.collection.mutable
import scala.util.Try

class RunnableDispatcher(
  registry : BridgeProviderRegistry,
  cf : IdAwareConnectionFactory,
  bs : DispatcherBuilderSupport,
  idSvc : ContainerIdentifierService,
  tMgr : FlowTransactionManager,
  routerCfg : ResourceTypeRouterConfig,
  streamsCfg : BlendedStreamsConfig
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  private val startedDispatchers : mutable.Map[String, ActorRef] = mutable.Map.empty
  private var transMgr : Option[ActorRef] = None
  private var transStream : Option[ActorRef] = None

  private val internal : BridgeProviderConfig = registry.internalProvider.get

  private[builder] def dispatcherSend(streamLogger : FlowEnvelopeLogger) : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val sendProducerSettings = JmsProducerSettings(
      log = streamLogger,
      connectionFactory = cf,
      headerCfg = bs.headerConfig,
      destinationResolver = s => new DispatcherDestinationResolver(s, registry, bs, streamLogger),
      logLevel = env => if (
        env.header[String](bs.headerConfig.headerBridgeVendor).contains(internal.vendor) &&
        env.header[String](bs.headerConfig.headerBridgeProvider).contains(internal.provider)
      ) {
        // In case the final destination lies within the internal JMS provider, we log the message sent as info
        LogLevel.Info
      } else {
        // Otherwise we log it as debug
        LogLevel.Debug
      }
    )

    jmsProducer(
      name = "dispatcherSend",
      autoAck = false,
      settings = sendProducerSettings
    )
  }

  // Simply stick the transaction event into the transaction destination
  private[builder] def transactionSend(streamLogger : FlowEnvelopeLogger)(implicit system : ActorSystem, materializer: Materializer) :
    Graph[FlowShape[FlowTransactionEvent, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val transform = b.add(Flow.fromFunction[FlowTransactionEvent, FlowEnvelope] { t =>
        FlowTransactionEvent.event2envelope(bs.headerConfig)(t)
          .withHeader(bs.headerConfig.headerTrackSource, streamLogger.underlying.name).get
      })

      val transactionSendSettings = JmsProducerSettings(
        log = streamLogger,
        headerCfg = bs.headerConfig,
        connectionFactory = cf,
        destinationResolver = s => new TransactionDestinationResolver(s, JmsDestination.asString(internal.transactions)),
        jmsDestination = None,
        deliveryMode = JmsDeliveryMode.Persistent,
        priority = 4,
        timeToLive = None,
        logLevel = _ => LogLevel.Debug
      )

      val producer = b.add(jmsProducer(
        name = "transactionSend",
        settings = transactionSendSettings,
        autoAck = false
      ))

      transform ~> producer
      FlowShape(transform.in, producer.out)
    }
  }

  private[builder] def transactionStream(tMgr : FlowTransactionManager) : Try[ActorRef] = Try {

    implicit val builderSupport : DispatcherBuilderSupport = bs

    new TransactionOutbound(
      headerConfig = bs.headerConfig,
      tMgr = tMgr,
      internalCf = cf,
      dispatcherCfg = routerCfg,
      transactionShard = streamsCfg.transactionShard,
      log = FlowEnvelopeLogger.create(bs.headerConfig, Logger(bs.headerConfig.prefix + ".transactions"))
    ).build()
  }

  def bridgeSource(
    internalProvider : BridgeProviderConfig,
    provider : BridgeProviderConfig,
    logger : FlowEnvelopeLogger
  ) : Source[FlowEnvelope, NotUsed] = {

    // todo : stick into config
    val settings = JMSConsumerSettings(
      log = logger,
      headerCfg = bs.headerConfig,
      connectionFactory = cf,
      sessionCount = 3,
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge,
      jmsDestination = if (provider.internal) {
        Some(provider.inbound)
      } else {
        val dest = s"${internalProvider.inbound.name}.${provider.vendor}.${provider.provider}"
        Some(JmsDestination.create(dest).get)
      },
      logLevel = _ => if (provider.internal) LogLevel.Info else LogLevel.Debug
    )

    val source = jmsConsumer(
      name = settings.jmsDestination.get.asString,
      settings = settings,
      minMessageDelay = None
    )

    if (provider.internal) {

      val setShard = streamsCfg.transactionShard match {
        case None => source
        case Some(shard) => source.via(Flow.fromFunction[FlowEnvelope, FlowEnvelope]{ env =>
          env.withHeader(bs.headerConfig.headerTransShard, shard, false).get
        })
      }

      val startTransaction = new TransactionWiretap(
        cf = cf,
        eventDest = provider.transactions,
        headerCfg = bs.headerConfig,
        inbound = true,
        trackSource = "internalDispatcher",
        log = logger
      ).flow()

      setShard.via(startTransaction)
    } else {
      source
    }
  }

  def start() : Unit = {

    try {
      // Get the internal provider
      val internalProvider = registry.internalProvider.get

      // The transaction stream will process the transaction events from the transactions destination
      transStream = Some(transactionStream(tMgr).get)

      // Create one dispatcher for each configured provider
      registry.allProvider.foreach { provider =>

        // Create a specific logger for each Dispatcher instance
        val dispLogger = Logger(bs.headerConfig.prefix + ".dispatcher." + provider.vendor + "." + provider.provider)
        val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(bs.headerConfig, dispLogger)

        // The blueprint for the dispatcher flow
        val dispatcher : Flow[FlowEnvelope, FlowTransactionEvent, NotUsed] =
          Flow.fromGraph(
            DispatcherBuilder(
              idSvc = idSvc,
              dispatcherCfg = routerCfg,
              envLogger = envLogger,
              sendFlow = dispatcherSend(envLogger),
            )(bs).dispatcher()
          )

        // Connect the consumer to a dispatcher
        val source : Source[FlowTransactionEvent, NotUsed] = bridgeSource(internalProvider, provider, envLogger).via(dispatcher)

        // Prepare and start the dispatcher
        val streamCfg = StreamControllerConfig.fromConfig(routerCfg.rawConfig).get.copy(name = dispLogger.name)

        // Wrap the dispatcher into a stream controller and make sure, the generated transaction events are sent to
        // the proper JMS destination
        val actor : ActorRef = system.actorOf(StreamController.props[FlowEnvelope, NotUsed](source.via(transactionSend(envLogger)), streamCfg))

        dispLogger.debug(s"Started dispatcher flow for provider [${provider.id}]")
        startedDispatchers.put(provider.id, actor)
      }
    } catch {
      case t : Throwable => Logger[RunnableDispatcher].error(t)(s"Failed to start dispatcher [${t.getMessage()}]")
    }
  }

  def stop() : Unit = {
    transMgr.foreach(system.stop)
    transStream.foreach(_ ! StreamController.Stop)
    startedDispatchers.foreach { case (_, d) => d ! StreamController.Stop }
    startedDispatchers.clear()
  }
}
