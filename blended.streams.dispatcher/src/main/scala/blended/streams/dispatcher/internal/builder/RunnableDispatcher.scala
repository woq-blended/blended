package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.persistence.PersistenceService
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionManager, TransactionWiretap}
import blended.streams.{StreamController, StreamControllerConfig}
import blended.util.logging.Logger

import scala.collection.mutable
import scala.util.Try

class RunnableDispatcher(
  registry : BridgeProviderRegistry,
  cf : IdAwareConnectionFactory,
  bs : DispatcherBuilderSupport,
  idSvc : ContainerIdentifierService,
  pSvc : PersistenceService,
  routerCfg : ResourceTypeRouterConfig
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  private val startedDispatchers : mutable.Map[String, ActorRef] = mutable.Map.empty
  private var transMgr : Option[ActorRef] = None
  private var transStream : Option[ActorRef] = None

  private[builder] def dispatcherSend() : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val sendProducerSettings = JmsProducerSettings(
      connectionFactory = cf,
      destinationResolver = s => new DispatcherDestinationResolver(s, registry, bs)
    )

    jmsProducer(
      name = "dispatcherSend",
      autoAck = false,
      settings = sendProducerSettings,
      log = bs.streamLogger
    )
  }

  // Simply stick the transaction event into the transaction destination
  private[builder] def transactionSend()(implicit system : ActorSystem, materializer: Materializer) :
    Graph[FlowShape[FlowTransactionEvent, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val internal = registry.internalProvider.get

      val transform = b.add(Flow.fromFunction[FlowTransactionEvent, FlowEnvelope] { t =>
        FlowTransactionEvent.event2envelope(bs.headerConfig)(t)
          .withHeader(bs.headerConfig.headerTrackSource, bs.streamLogger.name).get
      })

      val transactionSendSettings = JmsProducerSettings(
        connectionFactory = cf,
        jmsDestination = Some(internal.transactions),
        deliveryMode = JmsDeliveryMode.Persistent,
        priority = 4,
        timeToLive = None
      )

      val producer = b.add(jmsProducer(
        name = "transactionSend",
        settings = transactionSendSettings,
        autoAck = false,
        log = bs.streamLogger
      ))

      transform ~> producer
      FlowShape(transform.in, producer.out)
    }
  }

  private[builder] def transactionStream(tMgr : ActorRef) : Try[ActorRef] = Try {

    implicit val builderSupport : DispatcherBuilderSupport = bs

    new TransactionOutbound(
      headerConfig = bs.headerConfig,
      tMgr = tMgr,
      internalCf = cf,
      dispatcherCfg = routerCfg,
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
    val settings = JMSConsumerSettings(
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
      headerConfig = bs.headerConfig,
      log = logger
    )

    if (provider.internal) {
      val startTransaction = new TransactionWiretap(
        cf = cf,
        eventDest = provider.transactions,
        headerCfg = bs.headerConfig,
        inbound = true,
        trackSource = "internalDispatcher",
        log = bs.streamLogger
      ).flow()

      source.via(startTransaction)
    } else {
      source
    }
  }

  def start() : Unit = {

    try {
      // Get the internal provider
      val internalProvider = registry.internalProvider.get

      // We will create the Transaction Manager
      transMgr = Some(system.actorOf(FlowTransactionManager.props(pSvc)))

      // The transaction stream will process the transaction events from the transactions destination
      transStream = Some(transactionStream(transMgr.get).get)

      // The blueprint for the dispatcher flow
      val dispatcher : Flow[FlowEnvelope, FlowTransactionEvent, NotUsed] =
        Flow.fromGraph(builder.dispatcher())

      // Create one dispatcher for each configured provider
      registry.allProvider.foreach { provider =>

        // Create a specific logger for each Dispatcher instance
        val dispLogger = Logger(bs.streamLogger.name + "." + provider.vendor + "." + provider.provider)

        // Connect the consumer to a dispatcher
        val source = bridgeSource(internalProvider, provider, dispLogger).via(dispatcher)

        // Prepare and start the dispatcher
        val streamCfg = StreamControllerConfig(
          name = dispLogger.name,
          source = source.via(transactionSend())
        )

        val actor = system.actorOf(StreamController.props(streamCfg = streamCfg))

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
    startedDispatchers.foreach { case (k, d) => d ! StreamController.Stop }
    startedDispatchers.clear()
  }
}
