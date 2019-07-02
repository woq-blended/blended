package blended.streams.dispatcher.internal.builder

import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.utils.JmsDestination
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowHeaderConfig
import javax.jms.Session

import scala.concurrent.duration._
import scala.util.Try
import blended.util.RichTry._

class DispatcherDestinationResolver(
  override val settings : JmsProducerSettings,
  registry : BridgeProviderRegistry,
  bs : DispatcherBuilderSupport
) extends FlowHeaderConfigAware with JmsEnvelopeHeader {

  override def headerConfig : FlowHeaderConfig = bs.headerConfig

  override def sendParameter(session : Session, env : FlowEnvelope) : Try[JmsSendParameter] = Try {

    val internal = registry.internalProvider.unwrap

    val msg = createJmsMessage(session, env).unwrap

    val vendor : Option[String] = env.header[String](bs.headerBridgeVendor)
    val provider : Option[String] = env.header[String](bs.headerBridgeProvider)

    val dest : JmsDestination = (vendor, provider) match {
      case (Some(internal.vendor), Some(internal.provider)) =>
        JmsDestination.create(env.header[String](bs.headerBridgeDest).get).unwrap
      case (Some(v), Some(p)) =>
        val dest = s"${internal.outbound.name}.$v.$p"
        JmsDestination.create(dest).unwrap
      case (_, _) =>
        throw new Exception(s"[${bs.headerBridgeVendor}] and [${bs.headerBridgeProvider}] must be set in the message")
    }

    val delMode : JmsDeliveryMode = JmsDeliveryMode.create(env.header[String](deliveryModeHeader(bs.headerConfig.prefix)).get).unwrap
    val ttl : Option[FiniteDuration] = env.header[Long](bs.headerTimeToLive).map(_.millis)

    bs.streamLogger.debug(s"Sending envelope [${env.id}] to [$dest]")

    JmsSendParameter(
      message = msg,
      destination = dest,
      deliveryMode = delMode,
      priority = JmsSendParameter.defaultPriority,
      ttl = ttl
    )
  }
}
