package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge}
import akka.stream.{FanOutShape2, Graph}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal._
import blended.streams.jms.JmsEnvelopeHeader
import blended.streams.message.FlowEnvelope
import blended.streams.worklist.{WorklistEvent, WorklistStarted}
import blended.util.RichTry._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class DispatcherFanout(
  dispatcherCfg : ResourceTypeRouterConfig,
  idSvc : ContainerIdentifierService
)(implicit bs : DispatcherBuilderSupport) extends JmsEnvelopeHeader {

  /*-------------------------------------------------------------------------------------------------*/
  private[builder] val funFanoutOutbound : FlowEnvelope => Try[Seq[(OutboundRouteConfig, FlowEnvelope)]] = { env =>

    bs.withContextObject[ResourceTypeConfig, Seq[(OutboundRouteConfig, FlowEnvelope)]](bs.rtConfigKey, env) { rtCfg : ResourceTypeConfig =>
      Try {
        rtCfg.outbound.map { ob =>
          val obEnv =
            env
              .withContextObject(bs.outboundCfgKey, ob)
              .withHeader(bs.headerConfig.headerBranch, ob.id).unwrap
          (ob, outboundMsg(ob)(obEnv).unwrap)
        }
      }
    } match {
      case Right(s) => Success(s)
      case Left(t) =>
        bs.streamLogger.error(s"Exception in fan out step [${env.id}]")
        t.exception.foreach { e =>
          bs.streamLogger.error(e)(e.getMessage())
        }
        Failure(t.exception.getOrElse(new Exception("Unexpected exception")))
    }
  }

  private[builder] lazy val fanoutOutbound =
    FlowProcessor.transform[Seq[(OutboundRouteConfig, FlowEnvelope)]]("fanoutOutbound", bs.streamLogger)(funFanoutOutbound)

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val outboundMsg : OutboundRouteConfig => FlowEnvelope => Try[FlowEnvelope] = { outCfg => env =>

    val useHeaderBlock : OutboundHeaderConfig => Try[Boolean] = { oh =>
      Try {
        oh.condition match {
          // if the block does not have a condition, the header block will be used
          case None => true
          case Some(c) =>
            val resolve = idSvc.resolvePropertyString(c, env.flowMessage.header.mapValues(_.value))
            bs.streamLogger.debug(s"Resolved condition to [$resolve][${resolve.map(_.getClass().getName())}]")
            val use = resolve.map(_.asInstanceOf[Boolean]).unwrap

            val s = s"using header for [${env.id}]:[outboundMsg] block with expression [$c]"
            if (use) {
              bs.streamLogger.info(s)
            } else {
              bs.streamLogger.info("Not " + s)
            }
            use
        }
      }
    }

    Try {

      outCfg.outboundHeader.filter(b => useHeaderBlock(b).unwrap).foldLeft(env) {
        case (current, oh) =>
          var newEnv : FlowEnvelope = current
            .withHeader(bs.headerConfig.headerMaxRetries, oh.maxRetries).unwrap
            .withHeader(bs.headerAutoComplete, oh.autoComplete).unwrap

          if (oh.timeToLive >= 0L) {
            newEnv = newEnv.withHeader(bs.headerTimeToLive, oh.timeToLive).unwrap
          } else {
            newEnv = newEnv.removeHeader(bs.headerTimeToLive)
          }

          newEnv = newEnv
            .withHeader(deliveryModeHeader(bs.headerConfig.prefix), oh.deliveryMode).unwrap

          oh.header.foreach {
            case (header, value) =>
              val resolved = idSvc.resolvePropertyString(value, env.flowMessage.header.mapValues(_.value)).unwrap
              bs.streamLogger.trace(s"[${newEnv.id}]:[${outCfg.id}] - resolved property [$header] to [$resolved]")
              newEnv = newEnv.withHeader(header, resolved).unwrap
          }

          newEnv = if (oh.clearBody) {
            newEnv.copy(flowMessage = newEnv.flowMessage.clearBody())
          } else {
            newEnv
          }

          newEnv
            .withContextObject(bs.appHeaderKey, oh.applicationLogHeader)
            .withContextObject(bs.bridgeProviderKey, oh.bridgeProviderConfig)
            .withContextObject(bs.bridgeDestinationKey, oh.bridgeDestination)
      }
    }
  }

  private[builder] val toWorklist : Seq[(OutboundRouteConfig, FlowEnvelope)] => WorklistEvent = envelopes => {

    val timeout = envelopes.head._2.getFromContext[ResourceTypeConfig](bs.rtConfigKey) match {
      case Success(c) => c.map(_.timeout).getOrElse(10.seconds)
      case Failure(_) => 10.seconds
    }

    val wl = WorklistStarted(
      worklist = bs.worklist(envelopes.map(_._2) : _*).get,
      timeout = timeout
    )

    bs.streamLogger.debug(s"Created worklist event [$wl]")
    wl
  }

  private lazy val decideRouting = DispatcherOutbound(dispatcherCfg, idSvc)

  def build() : Graph[FanOutShape2[FlowEnvelope, FlowEnvelope, WorklistEvent], NotUsed] = {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val fanout = builder.add(fanoutOutbound)

      val errorFilter = builder.add(Broadcast[Either[FlowEnvelope, Seq[(OutboundRouteConfig, FlowEnvelope)]]](2))
      val withError = builder.add(Flow[Either[FlowEnvelope, Seq[(OutboundRouteConfig, FlowEnvelope)]]].filter(_.isLeft).map(_.left.get))
      val noError = builder.add(Flow[Either[FlowEnvelope, Seq[(OutboundRouteConfig, FlowEnvelope)]]].filter(_.isRight).map(_.right.get))
      val mapDestination = builder.add(decideRouting)

      val createWorklist = builder.add(Broadcast[Seq[(OutboundRouteConfig, FlowEnvelope)]](2))

      val envelopes = builder.add(Flow[Seq[(OutboundRouteConfig, FlowEnvelope)]].mapConcat(_.toList).map(_._2))
      val worklist = builder.add(Flow[Seq[(OutboundRouteConfig, FlowEnvelope)]].map(toWorklist))

      val merge = builder.add(Merge[FlowEnvelope](2))

      fanout ~> errorFilter ~> withError ~> merge
      errorFilter ~> noError ~> createWorklist.in

      createWorklist.out(0) ~> envelopes ~> mapDestination ~> merge
      createWorklist.out(1) ~> worklist

      new FanOutShape2(
        fanout.in,
        merge.out,
        worklist.out
      )
    }
  }
}
