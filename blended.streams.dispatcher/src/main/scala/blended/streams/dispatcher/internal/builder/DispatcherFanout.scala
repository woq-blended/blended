package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{FanOutShape2, Graph}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal._
import blended.streams.message.{BaseFlowMessage, FlowEnvelope}
import blended.streams.worklist.{Worklist, WorklistEvent, WorklistStarted}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class DispatcherFanout(
  dispatcherCfg : ResourceTypeRouterConfig,
  idSvc : ContainerIdentifierService
)(implicit bs : DispatcherBuilderSupport) {

  /*-------------------------------------------------------------------------------------------------*/
  private[builder] val funFanoutOutbound : FlowEnvelope => Try[Seq[(OutboundRouteConfig, FlowEnvelope)]] = { env =>

    Try {
      val fanouts = bs.withContextObject[ResourceTypeConfig, Seq[(OutboundRouteConfig, FlowEnvelope)]](bs.rtConfigKey, env) { rtCfg: ResourceTypeConfig =>
        Try {
          rtCfg.outbound.map { ob =>
            val obEnv =
              env
                .withContextObject(bs.outboundCfgKey, ob)
                .withHeader(bs.headerOutboundId, ob.id).get
            (ob, outboundMsg(ob)(obEnv).get)
          }
        }
      }

      fanouts.right.get
    }
  }
  private[builder] lazy val fanoutOutbound = FlowProcessor.transform[Seq[(OutboundRouteConfig, FlowEnvelope)]]("fanoutOutbound", bs.streamLogger)(funFanoutOutbound)

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val outboundMsg : OutboundRouteConfig => FlowEnvelope => Try[FlowEnvelope] = { outCfg => env =>

    val useHeaderBlock : OutboundHeaderConfig => Try[Boolean] = { oh =>
      Try {
        oh.condition match {
          // if the block does not have a condition, the header block will be used
          case None => true
          case Some(c) =>
            val use = idSvc.resolvePropertyString(c, env.flowMessage.header.mapValues(_.value)).map(_.asInstanceOf[Boolean]).get

            val s = s"using header for [${env.id}]:[outboundMsg] block with expression [$c]"
            if (use) {
              bs.streamLogger.info(s)
            } else {
              bs.streamLogger.info("Not " + s )
            }
            use
        }
      }
    }

    Try {
      var newEnv : FlowEnvelope = env
        .withHeader(bs.headerBridgeMaxRetry, outCfg.maxRetries).get
        .withHeader(bs.headerBridgeClose, outCfg.autoComplete).get

      if (outCfg.timeToLive > 0) {
        newEnv = newEnv.withHeader(bs.headerTimeToLive, outCfg.timeToLive).get
      }

      outCfg.outboundHeader.filter(b => useHeaderBlock(b).get).foreach { oh =>
        oh.header.foreach { case (header, value) =>
          val resolved = idSvc.resolvePropertyString(value, env.flowMessage.header.mapValues(_.value)).get
          bs.streamLogger.trace(s"[${newEnv.id}]:[${outCfg.id}] - resolved property [$header] to [$resolved]")
          newEnv = newEnv.withHeader(header, resolved).get
        }
      }

      if (outCfg.clearBody) {
        newEnv = newEnv.copy(flowMessage = BaseFlowMessage(newEnv.flowMessage.header))
      }

      newEnv.withContextObject(bs.appHeaderKey, outCfg.applicationLogHeader)
    }
  }

  private[builder] val toWorklist : Seq[(OutboundRouteConfig, FlowEnvelope)] => WorklistEvent =  envelopes => {

    val worklistId : String = envelopes.head._2.id

    val timeout = envelopes.head._2.getFromContext[ResourceTypeConfig](bs.rtConfigKey) match {
      case Success(c) => c.map(_.timeout).getOrElse(10.seconds)
      case Failure(_) => 10.seconds
    }

    val wl = WorklistStarted(
      worklist = bs.worklist(envelopes.map(_._2):_*).get,
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

      val wlLog = builder.add(Flow.fromFunction[WorklistEvent, WorklistEvent] { evt =>
        bs.streamLogger.trace(s"About to send worklist event [$evt]")
        evt
      })

      val merge = builder.add(Merge[FlowEnvelope](2))

      fanout ~> errorFilter ~> withError ~> merge
                errorFilter ~> noError ~> createWorklist.in

      createWorklist.out(0) ~> envelopes ~> mapDestination ~> merge
      createWorklist.out(1) ~> worklist ~> wlLog

      new FanOutShape2(
        fanout.in,
        merge.out,
        wlLog.out
      )
    }

  }

}
