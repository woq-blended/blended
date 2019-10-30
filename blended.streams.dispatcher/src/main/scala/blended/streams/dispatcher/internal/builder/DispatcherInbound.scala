package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.{ResourceTypeConfig, ResourceTypeRouterConfig}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.HeaderTransformProcessor
import blended.util.logging.LogLevel

import scala.util.Try

object DispatcherInbound {

  def apply(
    dispatcherCfg : ResourceTypeRouterConfig,
    idSvc : ContainerIdentifierService,
    streamLogger : FlowEnvelopeLogger
  )(implicit bs : DispatcherBuilderSupport) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    /*-------------------------------------------------------------------------------------------------*/
    /* Populate the message with the configured default headers                                        */
    /*-------------------------------------------------------------------------------------------------*/
    val defaultHeader : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
      Flow.fromGraph(
        HeaderTransformProcessor(
          name = "defaultHeader",
          log = streamLogger,
          rules = dispatcherCfg.defaultHeader,
          idSvc = Some(idSvc)
        ).flow(streamLogger).named("defaultHeader")
      )
    }

    /*-------------------------------------------------------------------------------------------------*/
    /* Make sure we do have a resourcetype in the message we can process                               */
    /*-------------------------------------------------------------------------------------------------*/
    val checkResourceType = FlowProcessor.fromFunction("checkResourceType", streamLogger) { env =>
      Try {
        env.header[String](bs.headerResourceType) match {
          case None =>
            throw new MissingResourceType(env.flowMessage)
          case Some(rt) =>
            dispatcherCfg.resourceTypeConfigs.get(rt) match {
              case None =>
                throw new IllegalResourceType(env.flowMessage, rt)
              case Some(rtCfg) =>
                rtCfg.outbound match {
                  case Nil =>
                    throw new MissingOutboundRouting(rt)
                  case _ =>
                    env.withContextObject(bs.rtConfigKey, rtCfg)
                }
            }
        }
      }
    }

    /*-------------------------------------------------------------------------------------------------*/
    val decideCbe = FlowProcessor.fromFunction("decideCbe", streamLogger) { env =>

      Try {
        bs.withContextObject[ResourceTypeConfig](bs.rtConfigKey, env, streamLogger){ rtCfg : ResourceTypeConfig =>

          if (rtCfg.withCBE) {
            val newMsg = env.flowMessage
              .withHeader(bs.headerEventVendor, dispatcherCfg.eventProvider.vendor).get
              .withHeader(bs.headerEventProvider, dispatcherCfg.eventProvider.provider).get
              .withHeader(bs.headerEventDest, dispatcherCfg.eventProvider.transactions.asString).get
              .withHeader(bs.headerCbeEnabled, true).get

            env.copy(flowMessage = newMsg)
          } else {
            env.withHeader(bs.headerCbeEnabled, false).get
          }
        }
      }
    }

    Flow.fromGraph(defaultHeader)
      .via(Flow.fromGraph(LogEnvelope(dispatcherCfg, "logInbound", LogLevel.Info, streamLogger)))
      .via(Flow.fromGraph(checkResourceType))
      .via(Flow.fromGraph(decideCbe))
  }

}
