package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionManager}
import blended.util.logging.Logger

import scala.collection.mutable
import scala.util.Try

class RunnableDispatcher(
  registry : BridgeProviderRegistry,
  cf : IdAwareConnectionFactory,
  bs : DispatcherBuilderSupport,
  idSvc : ContainerIdentifierService,
  routerCfg : ResourceTypeRouterConfig
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  private val switches : mutable.Map[String, KillSwitch] = mutable.Map.empty
  private var transMgr : Option[ActorRef] = None
  private var transStream : Option[KillSwitch] = None

  def dispatcherSend() : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val sendProducerSettings = JmsProducerSettings(
      headerConfig = bs.headerConfig,
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

  def transactionSend()(implicit system : ActorSystem, materializer: Materializer) : Sink[FlowTransactionEvent, NotUsed] = {

    val internal = registry.internalProvider.get

    val transactionSendSettings = JmsProducerSettings(
      headerConfig = bs.headerConfig,
      connectionFactory = cf,
      jmsDestination = Some(internal.transactions),
      deliveryMode = JmsDeliveryMode.Persistent,
      priority = 4,
      timeToLive = None
    )

    val transform = Flow.fromFunction[FlowTransactionEvent, FlowEnvelope] { t =>
      bs.streamLogger.debug(s"About to send transaction [$t]")
      FlowTransactionEvent.event2envelope(bs.headerConfig)(t)
    }

    val producer = jmsProducer(
      name = "transactionSend",
      settings = transactionSendSettings,
      autoAck = false,
      log = bs.streamLogger
    )

    transform.via(producer).to(Sink.ignore)
  }

  def transactionStream(tMgr : ActorRef) : Try[ActorRef] = Try {

    implicit val builderSupport = bs

    new TransactionOutbound(
      headerConfig = bs.headerConfig,
      tMgr = tMgr,
      internalCf = cf,
      dispatcherCfg = routerCfg,
      log = Logger(bs.headerConfig.prefix + ".transactions")
    ).build()
  }

  private val builder = DispatcherBuilder(
    idSvc = idSvc,
    dispatcherCfg = routerCfg,
    dispatcherSend()
  )(bs)

  def bridgeSource(
    provider : BridgeProviderConfig
  ) : Source[FlowEnvelope, NotUsed] = {

    // todo : stick into config
    val settings = JMSConsumerSettings(
      connectionFactory = cf,
      headerConfig = bs.headerConfig,
      sessionCount = 3,
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge,
      jmsDestination = if (provider.internal) {
        Some(provider.inbound)
      } else {
        val dest = s"${provider.inbound.name}.${provider.vendor}.${provider.provider}"
        Some(JmsDestination.create(dest).get)
      }
    )

    RestartableJmsSource(
      name = settings.jmsDestination.get.asString,
      settings = settings,
      log = bs.streamLogger
    )
  }

  def start() : Unit = {

    try {
      transMgr = Some(system.actorOf(FlowTransactionManager.props()))

      val transStream = Some(transactionStream(transMgr.get).get)

      val dispatcher : Flow[FlowEnvelope, FlowTransactionEvent, NotUsed] =
        Flow.fromGraph(builder.dispatcher())

      val transSend : Sink[FlowTransactionEvent, NotUsed] = transactionSend()

      registry.allProvider.foreach { provider =>
        val switch = bridgeSource(provider)
          .viaMat(KillSwitches.single)(Keep.right)
          .viaMat(dispatcher)(Keep.left)
          .toMat(transSend)(Keep.left)
          .run()

        bs.streamLogger.info(s"Started dispatcher flow for provider [${provider.id}]")
        switches.put(provider.id, switch)
      }
    } catch {
      case t : Throwable => bs.streamLogger.error(t)(s"Failed to start dispatcher [${t.getMessage()}]")
    }
  }

  def stop() : Unit = {
    transMgr.foreach(system.stop)
    transStream.foreach(_.shutdown())
    switches.foreach { case (k, s) => s.shutdown() }
    switches.clear()
  }
}
