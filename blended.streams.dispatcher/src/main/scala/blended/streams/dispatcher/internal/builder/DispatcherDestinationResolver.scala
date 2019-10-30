package blended.streams.dispatcher.internal.builder

import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.utils.JmsDestination
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.FlowHeaderConfig
import blended.util.logging.LogLevel
import javax.jms.Session

import scala.concurrent.duration._
import scala.util.Try

class DispatcherDestinationResolver(
  override val settings : JmsProducerSettings,
  registry : BridgeProviderRegistry,
  bs : DispatcherBuilderSupport,
  streamLogger : FlowEnvelopeLogger
) extends FlowHeaderConfigAware with JmsEnvelopeHeader {

  override def headerConfig: FlowHeaderConfig = bs.headerConfig

  override def sendParameter(session: Session, env: FlowEnvelope): Try[JmsSendParameter] = Try {

    val internal = registry.internalProvider.get

    val msg = createJmsMessage(session, env).get

    val vendor : String = env.header[String](bs.headerBridgeVendor).get
    val provider : String = env.header[String](bs.headerBridgeProvider).get

    val dest : JmsDestination = (vendor, provider) match {
      case (internal.vendor, internal.provider) =>
        JmsDestination.create(env.header[String](bs.headerBridgeDest).get).get
      case (v, p) =>
        val dest = s"${internal.outbound.name}.$v.$p"
        JmsDestination.create(dest).get
    }

    val delMode : JmsDeliveryMode = JmsDeliveryMode.create(env.header[String](deliveryModeHeader(bs.headerConfig.prefix)).get).get
    val ttl : Option[FiniteDuration] = env.header[Long](bs.headerTimeToLive).map(_.millis)

    streamLogger.logEnv(env, LogLevel.Debug, s"Sending envelope [${env.id}] to [$dest]")

    JmsSendParameter(
      message = msg,
      destination = dest,
      deliveryMode = delMode,
      priority = 4,
      ttl = ttl
    )
  }
}
