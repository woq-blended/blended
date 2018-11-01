package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.BridgeProviderConfig
import blended.jms.utils.{JmsDestination, JmsQueue}
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.{ProviderResolver, ResourceTypeRouterConfig}
import blended.streams.jms.JmsFlowSupport
import blended.streams.message.FlowEnvelope
import blended.util.logging.LogLevel

import scala.util.Try

object DispatcherOutbound {

  def apply(dispatcherCfg: ResourceTypeRouterConfig, idSvc : ContainerIdentifierService)(implicit bs : DispatcherBuilderSupport)
    : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    /*-------------------------------------------------------------------------------------------------*/
    val routingDecider = FlowProcessor.fromFunction("routingDecider", bs.streamLogger) { env =>

      Try {
        bs.withContextObject[BridgeProviderConfig](bs.bridgeProviderKey, env) { provider =>
          bs.withContextObject[Option[JmsDestination]](bs.bridgeDestinationKey, env) { dest =>

            val outId = env.header[String](bs.headerOutboundId).getOrElse("default")

            val p = (env.header[String](bs.headerBridgeVendor), env.header[String](bs.headerBridgeProvider)) match {
              case (Some(v), Some(p)) =>
                val vendor = idSvc.resolvePropertyString(v).map(_.toString()).get
                val provider = idSvc.resolvePropertyString(p).map(_.toString()).get
                ProviderResolver.getProvider(dispatcherCfg.providerRegistry, vendor, provider).get
              case (_, _) => provider
            }

            val mappedDest : JmsDestination = env.header[String](bs.headerBridgeDest) match {
              case Some(d) => JmsDestination.create(idSvc.resolvePropertyString(d).map(_.toString).get).get
              case None => dest.getOrElse(JmsQueue("replyTo"))
            }

            val resolvedDest = mappedDest match {
              case r@JmsQueue("replyTo") =>
                env.header[String](JmsFlowSupport.replyToHeader(bs.prefix)).map(s => JmsDestination.create(s).get) match {
                  case None => throw new JmsDestinationMissing(env, outId)
                  case Some(r) => r
                }
              case o => o
            }

            bs.streamLogger.info(s"Routing for [${env.id}] is [${p.id}:$resolvedDest]")

            val r = env
              .withHeader(bs.headerBridgeVendor, p.vendor).get
              .withHeader(bs.headerBridgeProvider, p.provider).get
              .withHeader(bs.headerBridgeDest, resolvedDest.asString).get

            r
          }
        }
      }
    }

    Flow.fromGraph(routingDecider).via(Flow.fromGraph(routingDecider))
  }

}
