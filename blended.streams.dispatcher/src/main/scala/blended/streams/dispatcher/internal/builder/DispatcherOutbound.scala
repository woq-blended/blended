package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.JmsDestination
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms.JmsFlowSupport
import blended.streams.message.FlowEnvelope

import scala.util.Try

object DispatcherOutbound {

  private[builder] case class DispatcherTarget(
    vendor : String,
    provider : String,
    dest : JmsDestination
  )

  private[builder] def resolveProvider(
    registry : BridgeProviderRegistry,
    vendor : Option[String],
    provider : Option[String]
  ) : Try[BridgeProviderConfig] = Try {
    (vendor, provider) match {
      case (Some(v), Some(p)) => registry.jmsProvider(v,p) match {
        case None => throw new Exception(s"Could not resolve provider [$vendor:$provider]")
        case Some(r) => r
      }
      case (_,_) => throw new Exception(s"Could not resolve provider [$vendor:$provider]")
    }
  }

  val resolveDest : DispatcherBuilderSupport => FlowEnvelope => Try[JmsDestination] = { bs => env => Try {

    env.getFromContext[Option[JmsDestination]](bs.bridgeDestinationKey).get match {
      case None => throw new Exception(s"Failed to resolve context object [${bs.bridgeDestinationKey}]")
      case Some(ctxtDest) => ctxtDest match {
        case None => JmsDestination.create(JmsFlowSupport.replyToQueueName).get
        case Some(d) => d
      }
    }
  }}


  private[builder] def outboundRouting(
    dispatcherCfg : ResourceTypeRouterConfig,
    bs : DispatcherBuilderSupport
  )(env: FlowEnvelope) : Try[DispatcherTarget] = Try {

    val dest = resolveDest(bs)(env).get
    bs.streamLogger.debug(s"Resolved routing destination to [$dest]")

    val targetDest : JmsDestination = dest.name match {
      case JmsFlowSupport.replyToQueueName =>
        val replyToHeader : String = JmsFlowSupport.replyToHeader(bs.headerConfig.prefix)
        env.header[String](replyToHeader) match {
          case None => throw new Exception(s"Header [$replyToHeader] must be set for replyTo dispatcher flow")
          case Some(s) => JmsDestination.create(s).get
        }

      case _ => dest
    }

    val bridgeProvider : BridgeProviderConfig = dest.name match {
      // For the replyto destination we have to respond to the src provider
      case JmsFlowSupport.replyToQueueName =>
        val v = env.header[String](bs.srcVendorHeader(bs.headerConfig.prefix))
        val p = env.header[String](bs.srcProviderHeader(bs.headerConfig.prefix))
        resolveProvider(dispatcherCfg.providerRegistry,v,p).get
      case _ =>
        env.getFromContext[BridgeProviderConfig](bs.bridgeProviderKey).get match {
          case None => throw new Exception(s"Failed to resolve context object [${bs.bridgeProviderKey}]")
          case Some(p) => p
        }
    }

    bs.streamLogger.debug(s"Resolved routing provider to [$bridgeProvider]")

    DispatcherTarget(
      bridgeProvider.vendor, bridgeProvider.provider, targetDest
    )
  }

  def apply(dispatcherCfg: ResourceTypeRouterConfig, idSvc : ContainerIdentifierService)(implicit bs : DispatcherBuilderSupport)
    : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    /*-------------------------------------------------------------------------------------------------*/
    val routingDecider = FlowProcessor.fromFunction("routingDecider", bs.streamLogger) { env => Try {

      val routing = outboundRouting(dispatcherCfg, bs)(env).get

      env
        .withHeader(bs.headerBridgeVendor, routing.vendor).get
        .withHeader(bs.headerBridgeProvider, routing.provider).get
        .withHeader(bs.headerBridgeDest, routing.dest.asString).get
    }}

    Flow.fromGraph(routingDecider).via(Flow.fromGraph(routingDecider))
  }
}
