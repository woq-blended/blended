package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, Materializer}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.FlowHeaderConfig
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.util.logging.LogLevel

import scala.util.Try

class CbeSendFlow(
  headerConfig : FlowHeaderConfig,
  dispatcherCfg : ResourceTypeRouterConfig,
  internalCf: IdAwareConnectionFactory,
  streamLogger: FlowEnvelopeLogger
)(implicit system : ActorSystem, bs: DispatcherBuilderSupport) extends JmsStreamSupport with JmsEnvelopeHeader {

  private implicit val materializer : Materializer = ActorMaterializer()
  private val config = dispatcherCfg.providerRegistry.mandatoryProvider(internalCf.vendor, internalCf.provider)

  private[builder] val cbeSink : Try[Flow[FlowEnvelope, FlowEnvelope, NotUsed]] = Try {

    val resolver : JmsProducerSettings => JmsDestinationResolver = settings => new DispatcherDestinationResolver(
      settings = settings,
      registry = dispatcherCfg.providerRegistry,
      bs = bs,
      streamLogger = streamLogger
    )

    val sinkSettings = JmsProducerSettings(
      log = streamLogger,
      headerCfg = headerConfig,
      connectionFactory = internalCf,
      jmsDestination = Some(config.get.cbes),
      deliveryMode = JmsDeliveryMode.Persistent,
      destinationResolver = resolver
    )

    val prepareCbe = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>

      val v = env.header[String](bs.headerEventVendor) match {
        case None    => dispatcherCfg.eventProvider.vendor
        case Some(s) => s
      }

      val p = env.header[String](bs.headerEventProvider) match {
        case None    => dispatcherCfg.eventProvider.provider
        case Some(s) => s
      }

      val provider = dispatcherCfg.providerRegistry.jmsProvider(v, p).get

      val result = env
        .withHeader(bs.headerBridgeVendor, v).get
        .withHeader(bs.headerBridgeProvider, p).get
        .withHeader(bs.headerBridgeDest, provider.cbes.asString).get
        .withHeader(deliveryModeHeader(bs.headerConfig.prefix), JmsDeliveryMode.Persistent.asString).get
        .withHeader(bs.headerConfig.headerTrack, false).get

      streamLogger.logEnv(env, LogLevel.Debug, s"Prepared to send CBE for envelope [${env.id}]")
      result
    }

    prepareCbe
      .via(jmsProducer(
        name = "cbeOutbound",
        settings = sinkSettings,
        autoAck = false
      ))
  }

  def build() : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = cbeSink.get
}
