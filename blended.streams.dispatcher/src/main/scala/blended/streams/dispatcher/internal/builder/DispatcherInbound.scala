package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.{ResourceTypeConfig, ResourceTypeRouterConfig}
import blended.streams.message.FlowEnvelope
import blended.streams.processor.HeaderTransformProcessor
import blended.util.logging.LogLevel

import scala.util.Try

object DispatcherInbound {

  def apply(
    dispatcherCfg : ResourceTypeRouterConfig,
    idSvc : ContainerIdentifierService
  )(implicit bs : DispatcherBuilderSupport) : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    /*-------------------------------------------------------------------------------------------------*/
    /* Populate the message with the configured default headers                                        */
    /*-------------------------------------------------------------------------------------------------*/
    val defaultHeader = HeaderTransformProcessor(
      name = "defaultHeader",
      log = bs.streamLogger,
      rules = dispatcherCfg.defaultHeader.map(h => (h.name, h.value, h.overwrite)),
      idSvc = Some(idSvc)
    ).flow(Some(bs.streamLogger))

    /*-------------------------------------------------------------------------------------------------*/
    /* Make sure we do have a resourcetype in the message we can process                               */
    /*-------------------------------------------------------------------------------------------------*/
    val checkResourceType = FlowProcessor.fromFunction("checkResourceType", bs.streamLogger) { env =>
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
    val decideCbe = FlowProcessor.fromFunction("decideCbe", bs.streamLogger) { env =>

      Try {
        bs.withContextObject[ResourceTypeConfig](bs.rtConfigKey, env){ rtCfg : ResourceTypeConfig =>

          if (rtCfg.withCBE) {
            val newMsg = env.flowMessage
              .withHeader(bs.headerEventVendor, dispatcherCfg.eventProvider.vendor).get
              .withHeader(bs.headerEventProvider, dispatcherCfg.eventProvider.provider).get
              .withHeader(bs.headerEventDest, dispatcherCfg.eventProvider.eventDestination.asString).get
              .withHeader(bs.headerCbeEnabled, true).get

            env.copy(flowMessage = newMsg)
          } else {
            env.withHeader(bs.headerCbeEnabled, false).get
          }
        }
      }
    }

    Flow.fromGraph(defaultHeader)
      .via(Flow.fromGraph(LogEnvelope(dispatcherCfg, "logInbound", LogLevel.Info)))
      .via(Flow.fromGraph(checkResourceType))
      .via(Flow.fromGraph(decideCbe))
  }

}
