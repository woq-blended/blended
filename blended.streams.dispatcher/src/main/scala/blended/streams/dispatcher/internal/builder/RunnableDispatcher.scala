package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.jmx.statistics.ServiceInvocationReporter
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionManager, TransactionDestinationResolver, TransactionWiretap}
import blended.streams.{BlendedStreamsConfig, FlowProcessor, StreamController}
import blended.util.logging.Logger
import blended.util.RichTry._

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

  private[builder] def dispatcherSend() : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val sendProducerSettings = JmsProducerSettings(
      log = bs.streamLogger,
      connectionFactory = cf,
      headerCfg = bs.headerConfig,
      destinationResolver = s => new DispatcherDestinationResolver(s, registry, bs)
    )

    jmsProducer(
      name = "dispatcherSend",
      autoAck = false,
      settings = sendProducerSettings
    )
  }

  // Simply stick the transaction event into the transaction destination
  private[builder] def transactionSend()(implicit system : ActorSystem, materializer : Materializer) : Graph[FlowShape[FlowTransactionEvent, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val transform = b.add(Flow.fromFunction[FlowTransactionEvent, FlowEnvelope] { t =>
        FlowTransactionEvent.event2envelope(bs.headerConfig)(t)
          .withHeader(bs.headerConfig.headerTrackSource, bs.streamLogger.name).get
      })

      val transactionSendSettings = JmsProducerSettings(
        log = bs.streamLogger,
        headerCfg = bs.headerConfig,
        connectionFactory = cf,
        destinationResolver = s => new TransactionDestinationResolver(s, JmsDestination.asString(internal.transactions)),
        jmsDestination = None,
        deliveryMode = JmsDeliveryMode.Persistent,
        priority = JmsSendParameter.defaultPriority,
        timeToLive = None
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
      log = Logger(bs.headerConfig.prefix + ".transactions")
    ).build()
  }

  private[builder] val builder = DispatcherBuilder(
    idSvc = idSvc,
    dispatcherCfg = routerCfg,
    dispatcherSend()
  )(bs)

  def bridgeSource(
    internalProvider : BridgeProviderConfig,
    provider : BridgeProviderConfig,
    logger : Logger
  ) : Source[FlowEnvelope, NotUsed] = {

    // todo : stick into config
    val settings = JmsConsumerSettings(
      log = bs.streamLogger,
      headerCfg = bs.headerConfig,
      connectionFactory = cf,
      sessionCount = 3,
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge,
      jmsDestination = if (provider.internal) {
        Some(provider.inbound)
      } else {
        val dest = s"${internalProvider.inbound.name}.${provider.vendor}.${provider.provider}"
        Some(JmsDestination.create(dest).get)
      }
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
        log = bs.streamLogger
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

      // The blueprint for the dispatcher flow
      val dispatcher : Flow[FlowEnvelope, FlowTransactionEvent, NotUsed] =
        Flow.fromGraph(builder.dispatcher())

      // Create one dispatcher for each configured provider
      registry.allProvider.foreach { provider =>

        // Create a specific logger for each Dispatcher instance
        val dispLogger = Logger(bs.streamLogger.name + "." + provider.vendor + "." + provider.provider)

        val startStats : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = FlowProcessor.fromFunction("startStats", bs.streamLogger){ env => Try {
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
          bridgeSource(internalProvider, provider, dispLogger)
            .via(startStats)
            .via(dispatcher)

        // Wrap the dispatcher into a stream controller and make sure, the generated transaction events are sent to
        // the proper JMS destination
        val actor : ActorRef = system.actorOf(StreamController.props[FlowEnvelope, NotUsed](
          streamName = dispLogger.name,
          src = source.via(transactionSend()),
          streamCfg = streamsCfg
        )(onMaterialize = _ => ()))

        bs.streamLogger.info(s"Started dispatcher flow for provider [${provider.id}]")
        startedDispatchers.put(provider.id, actor)
      }
    } catch {
      case t : Throwable => bs.streamLogger.error(t)(s"Failed to start dispatcher [${t.getMessage()}]")
    }
  }

  def stop() : Unit = {
    transMgr.foreach(system.stop)
    transStream.foreach(_ ! StreamController.Stop)
    startedDispatchers.foreach { case (_, d) => d ! StreamController.Stop }
    startedDispatchers.clear()
  }
}
