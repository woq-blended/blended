package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream.{FlowShape, Graph}
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.{OutboundRouteConfig, ResourceTypeRouterConfig}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.util.logging.LogLevel.LogLevel

import scala.util.{Failure, Success, Try}

/*-------------------------------------------------------------------------------------------------*/
/* Perform a logging step                                                                          */
/*-------------------------------------------------------------------------------------------------*/
object LogEnvelope {

  def apply(dispatcherCfg: ResourceTypeRouterConfig, stepName : String, level: LogLevel, logger : FlowEnvelopeLogger)(implicit bs: DispatcherBuilderSupport) :
    Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] =

    FlowProcessor.fromFunction( stepName, logger) { env =>

      Try {

        val outCfg : Option[OutboundRouteConfig] = env.getFromContext[OutboundRouteConfig](bs.outboundCfgKey) match {
          case Success(o) => o
          case Failure(_) => None
        }

        val logHeader : List[String] = env.getFromContext[List[String]](bs.appHeaderKey) match {
          case Success(l) => l.getOrElse(List.empty)
          case Failure(_) => dispatcherCfg.applicationLogHeader
        }

        val headerString : Map[String, String] = logHeader match {
          case Nil => env.flowMessage.header.mapValues(_.toString)
          case l => l.map { h =>
            (h -> env.flowMessage.header.get(h).map(_.toString()).getOrElse("UNKNOWN"))
          }.toMap
        }

        val id = s"[${env.id}]:[$stepName]" + outCfg.map(c => s":[${c.id}]").getOrElse("")

        env
      }
    }
}

