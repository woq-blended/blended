package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.BridgeProviderConfig
import blended.jms.utils.{JmsDestination, JmsQueue}
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.{OutboundRouteConfig, ProviderResolver, ResourceTypeRouterConfig}
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
        bs.withContextObject[OutboundRouteConfig](bs.outboundCfgKey, env) { outCfg =>

          val provider : BridgeProviderConfig =

            (env.header[String](bs.headerBridgeVendor), env.header[String](bs.headerBridgeProvider)) match {
              case (Some(v), Some(p)) =>
                val vendor = idSvc.resolvePropertyString(v).map(_.toString()).get
                val provider = idSvc.resolvePropertyString(p).map(_.toString()).get
                ProviderResolver.getProvider(dispatcherCfg.providerRegistry, vendor, provider).get

              case (_, _) => outCfg.bridgeProvider
            }

          val dest : JmsDestination = env.header[String](bs.headerBridgeDest) match {
            case Some(d) => JmsDestination.create(idSvc.resolvePropertyString(d).map(_.toString).get).get
            case None => outCfg.bridgeDestination match {
              case None => throw new JmsDestinationMissing(env, outCfg)
              case Some(d) => if (d == JmsQueue("replyTo")) {
                env.header[String](JmsFlowSupport.replyToHeader(bs.prefix)).map(s => JmsDestination.create(s).get) match {
                  case None => throw new JmsDestinationMissing(env, outCfg)
                  case Some(r) => r
                }
              } else {
                d
              }
            }
          }

          bs.streamLogger.info(s"Routing for [${env.id}] is [${provider.id}:${dest}]")

          env
            .withHeader(bs.headerBridgeVendor, provider.vendor).get
            .withHeader(bs.headerBridgeProvider, provider.provider).get
            .withHeader(bs.headerBridgeDest, dest.asString).get

        }
      }
    }

    Flow
      .fromGraph(Flow.fromGraph(LogEnvelope(dispatcherCfg, "logOutbound", LogLevel.Info)))
      .via(Flow.fromGraph(routingDecider))
  }

}
