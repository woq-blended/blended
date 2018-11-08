package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowTransactionEvent
import javax.jms.Session

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

class RunnableDispatcher(
  registry : BridgeProviderRegistry,
  cf : IdAwareConnectionFactory,
  bs : DispatcherBuilderSupport,
  idSvc : ContainerIdentifierService,
  routerCfg : ResourceTypeRouterConfig
)(implicit system: ActorSystem, materializer: Materializer) extends JmsStreamSupport {

  private val switches : mutable.Map[String, KillSwitch] = mutable.Map.empty

  class DispatcherDestinationResolver(
    override val settings : JmsProducerSettings,
    registry : BridgeProviderRegistry,
    bs : DispatcherBuilderSupport
  ) extends JmsDestinationResolver with JmsEnvelopeHeader {
    override def sendParameter(session: Session, env: FlowEnvelope): Try[JmsSendParameter] = Try {

      val internal = registry.internalProvider.get

      val msg = createJmsMessage(session, env)

      val vendor : String = env.header[String](bs.headerBridgeVendor).get
      val provider : String = env.header[String](bs.headerBridgeProvider).get

      val dest : JmsDestination = (vendor, provider) match {
        case (internal.vendor, internal.provider) =>
          JmsDestination.create(env.header[String](bs.headerBridgeDest).get).get
        case (v, p) =>
          val dest = s"${internal.inbound.name}.$v.$p"
          JmsDestination.create(dest).get
      }

      val delMode : JmsDeliveryMode = JmsDeliveryMode.create(env.header[String](bs.headerDeliveryMode).get).get
      val ttl : Option[FiniteDuration] = env.header[Long](bs.headerTimeToLive).map(_.millis)

      bs.streamLogger.debug(s"Sending envelope [${env.id}] to [$dest]")

      JmsSendParameter(
        message = msg,
        destination = dest,
        deliveryMode = delMode,
        priority = 4,
        ttl = ttl
      )
    }
  }

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

  private val builder = DispatcherBuilder(
    idSvc = idSvc,
    dispatcherCfg = routerCfg
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

    val dispatcher : Flow[FlowEnvelope, FlowTransactionEvent, NotUsed] =
      Flow.fromGraph(builder.dispatcher(dispatcherSend()))

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
  }

  def stop() : Unit = {
    switches.foreach { case (k, s) => s.shutdown() }
    switches.clear()
  }
}
