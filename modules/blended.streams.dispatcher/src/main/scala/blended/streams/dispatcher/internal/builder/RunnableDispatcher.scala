package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Source}
import blended.container.context.api.ContainerContext
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{Connected, ConnectionStateChanged, IdAwareConnectionFactory, JmsDestination, QueryConnectionState}
import blended.jmx.statistics.ServiceInvocationReporter
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionManager, TransactionDestinationResolver, TransactionWiretap}
import blended.streams.{BlendedStreamsConfig, FlowProcessor, StreamController}
import blended.util.logging.{LogLevel, Logger}
import blended.util.RichTry._

import scala.collection.mutable
import scala.util.Try
import scala.util.control.NonFatal

class RunnableDispatcher(
  registry : BridgeProviderRegistry,
  cf : IdAwareConnectionFactory,
  bs : DispatcherBuilderSupport,
  ctCtxt : ContainerContext,
  tMgr : FlowTransactionManager,
  routerCfg : ResourceTypeRouterConfig,
  streamsCfg : BlendedStreamsConfig
)(implicit system: ActorSystem) extends JmsStreamSupport {

  private val startedDispatchers : mutable.Map[String, ActorRef] = mutable.Map.empty
  private var transStream : Option[ActorRef] = None

  private val internal : BridgeProviderConfig = registry.internalProvider.get

  private[builder] def dispatcherSend(streamLogger : FlowEnvelopeLogger) : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {
      GraphDSL.create() { implicit b =>

        import GraphDSL.Implicits._

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

        val clearRetrying = b.add(
          FlowProcessor.fromFunction("clearRetrying", streamLogger){ env => Try {
            env.removeHeader(bs.headerConfig.headerRetrying)
          }}
        )

        val performSend = b.add(jmsProducer(
          name = "dispatcherSend",
          autoAck = false,
          settings = sendProducerSettings
        ))

        clearRetrying ~> performSend

        FlowShape(clearRetrying.in, performSend.out)
      }
    }

    Flow.fromGraph(g)
  }

  // Simply stick the transaction event into the transaction destination
  private[builder] def transactionSend(streamLogger : FlowEnvelopeLogger)(implicit system : ActorSystem) :
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
        priority = JmsSendParameter.defaultPriority,
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
      streamsCfg = streamsCfg,
      log = FlowEnvelopeLogger.create(bs.headerConfig, Logger(bs.headerConfig.prefix + ".transactions"))
    ).build()
  }

  def bridgeSource(
    internalProvider : BridgeProviderConfig,
    provider : BridgeProviderConfig,
    logger : FlowEnvelopeLogger
  ) : Source[FlowEnvelope, NotUsed] = {

    // todo : stick into config
    val settings = JmsConsumerSettings(
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
        case Some(shard) => source.via(Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
          env.withHeader(bs.headerConfig.headerTransShard, shard, overwrite = false).get
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

  private def sendStartupMessages(providerCfg : BridgeProviderConfig) : Unit = {

    val logger = FlowEnvelopeLogger.create(
      bs.headerConfig,
      Logger(bs.headerConfig.prefix + ".dispatcher." + providerCfg.vendor + "." + providerCfg.provider + ".startup")
    )

    try {
      if (routerCfg.startupMap.nonEmpty) {

        logger.underlying.debug(s"Waiting for JMS connection [${providerCfg.vendor},${providerCfg.provider}] to connect ...")

        val stateActor : ActorRef = system.actorOf(Props(new Actor {

          val prodSettings: JmsProducerSettings = JmsProducerSettings(
            log = logger,
            headerCfg = bs.headerConfig,
            connectionFactory = cf,
            jmsDestination = Some(providerCfg.inbound),
            deliveryMode = JmsDeliveryMode.NonPersistent,
            timeToLive = None
          )

          val msgs : Seq[FlowEnvelope] = routerCfg.startupMap.map { case (k, v) =>
            FlowEnvelope(FlowMessage(v)(FlowMessage.props(bs.headerConfig.headerResourceType -> k).unwrap))
          }.toSeq

          override def receive: Receive = {
            case e : ConnectionStateChanged if e.state.status == Connected && providerCfg.vendor == e.state.vendor && providerCfg.provider == e.state.provider =>
              logger.underlying.debug(s"Sending [${msgs.size}] messages to [${providerCfg.vendor}:${providerCfg.provider}:${providerCfg.inbound.asString}]")
              sendMessages(prodSettings, logger, msgs:_*).unwrap
              context.stop(self)
          }
        }))

        system.eventStream.subscribe(stateActor, classOf[ConnectionStateChanged])
        system.eventStream.publish(QueryConnectionState(providerCfg.vendor, providerCfg.provider))

      } else {
        logger.underlying.debug("No startup messages configured")
      }
    } catch {
      case NonFatal(e) =>
        logger.underlying.warn(s"Failed to send startup messages [${e.getMessage()}]")
    }
  }

  def start() : Unit = {

    try {
      // Get the internal provider
      val internalProvider : BridgeProviderConfig = registry.internalProvider.get
      sendStartupMessages(internalProvider)

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
              ctCtxt = ctCtxt,
              dispatcherCfg = routerCfg,
              envLogger = envLogger,
              sendFlow = dispatcherSend(envLogger),
            )(bs).dispatcher()
          )

        val startStats : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = FlowProcessor.fromFunction("startStats", envLogger){ env => Try {
          val resType : String = env.header[String](bs.headerConfig.headerResourceType).getOrElse("UNKNOWN")
          val id : String = ServiceInvocationReporter.invoked(
            component = "dispatcher",
            subComponents = Map(
              "jmsvendor" -> provider.vendor,
              "provider" -> provider.provider,
              "resourcetype" -> resType
            )
          )
          env.withHeader(bs.headerConfig.headerStatsId, id).unwrap
        }}

        // Connect the consumer to a dispatcher
        val source : Source[FlowTransactionEvent, NotUsed] =
          bridgeSource(internalProvider, provider, envLogger)
            .via(startStats)
            .via(dispatcher)

        // Wrap the dispatcher into a stream controller and make sure, the generated transaction events are sent to
        // the proper JMS destination
        val actor : ActorRef = system.actorOf(StreamController.props[FlowEnvelope, NotUsed](
          streamName = dispLogger.name,
          src = source.via(transactionSend(envLogger)),
          streamCfg = streamsCfg
        )(onMaterialize = _ => ()))

        dispLogger.debug(s"Started dispatcher flow for provider [${provider.id}]")
        startedDispatchers.put(provider.id, actor)
      }
    } catch {
      case t : Throwable => Logger[RunnableDispatcher].error(t)(s"Failed to start dispatcher [${t.getMessage()}]")
    }
  }

  def stop() : Unit = {
    transStream.foreach(_ ! StreamController.Stop)
    startedDispatchers.foreach { case (_, d) => d ! StreamController.Stop }
    startedDispatchers.clear()
  }
}
