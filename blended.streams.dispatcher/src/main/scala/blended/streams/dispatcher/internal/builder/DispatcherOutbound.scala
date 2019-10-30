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
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.util.logging.LogLevel

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

  val resolveDest : ContainerIdentifierService => DispatcherBuilderSupport => FlowEnvelope => Try[JmsDestination] = {
    idSvc => bs => env => Try {

    env.getFromContext[Option[String]](bs.bridgeDestinationKey).get match {
      case None => throw new Exception(s"Failed to resolve context object [${bs.bridgeDestinationKey}]")
      case Some(ctxtDest) => ctxtDest match {
        case None => JmsDestination.create(JmsFlowSupport.replyToQueueName).get
        case Some(d) =>

          val name : String = idSvc.resolvePropertyString(
            value = d,
            additionalProps = env.flowMessage.header.mapValues(_.value)
          ).map(_.toString()).get

          JmsDestination.create(name).get

      }
    }
  }}

  private[builder] def outboundRouting(
    dispatcherCfg : ResourceTypeRouterConfig,
    idSvc : ContainerIdentifierService,
    bs : DispatcherBuilderSupport,
    streamLogger : FlowEnvelopeLogger
  )(env: FlowEnvelope) : Try[DispatcherTarget] = Try {

    val dest : JmsDestination = resolveDest(idSvc)(bs)(env).get

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

        val v : Option[String] = env.header[String](bs.srcVendorHeader(bs.headerConfig.prefix)).map{ s =>
          idSvc.resolvePropertyString(s).map(_.toString()).get
        }

        val p : Option[String] = env.header[String](bs.srcProviderHeader(bs.headerConfig.prefix)).map{ s =>
          idSvc.resolvePropertyString(s).map(_.toString()).get
        }

        resolveProvider(dispatcherCfg.providerRegistry,v,p).get

      case _ =>
        env.getFromContext[BridgeProviderConfig](bs.bridgeProviderKey).get match {
          case None => throw new Exception(s"Failed to resolve context object [${bs.bridgeProviderKey}]")
          case Some(p) => p
        }
    }

    val result = DispatcherTarget(
      bridgeProvider.vendor, bridgeProvider.provider, targetDest
    )
    streamLogger.logEnv(env, LogLevel.Info, s"Routing for [${env.id}] is [$result]")

    result
  }

  def apply(dispatcherCfg: ResourceTypeRouterConfig, idSvc : ContainerIdentifierService, streamLogger : FlowEnvelopeLogger)(implicit bs : DispatcherBuilderSupport)
    : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    /*-------------------------------------------------------------------------------------------------*/
    val routingDecider = FlowProcessor.fromFunction("routingDecider", streamLogger) { env => Try {

      val routing = outboundRouting(dispatcherCfg, idSvc, bs, streamLogger)(env).get

      env
        .withHeader(bs.headerBridgeVendor, routing.vendor).get
        .withHeader(bs.headerBridgeProvider, routing.provider).get
        .withHeader(bs.headerBridgeDest, routing.dest.asString).get
        .withHeader(bs.headerConfig.headerTrack, true).get
    }}

    Flow.fromGraph(routingDecider)
  }
}
