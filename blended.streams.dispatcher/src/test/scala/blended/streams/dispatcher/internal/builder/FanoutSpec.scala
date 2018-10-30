package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.{GraphDSL, Keep, Source}
import blended.streams.dispatcher.internal.OutboundRouteConfig
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.testsupport.Collector
import blended.streams.worklist.{WorklistEvent, WorklistState}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class FanoutSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport {

  override def country: String = "cc"
  override def location: String = "09999"
  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()
  override def loggerName: String = getClass().getName()

  implicit val bs = new DispatcherBuilderSupport {
    override val prefix: String = "App"
    override val streamLogger: Logger = Logger(loggerName)
  }

  def performFanout(
    ctxt : DispatcherExecContext,
    fanout : DispatcherFanout,
    resType : String,
    envelope : FlowEnvelope
  ) : Try[Seq[(OutboundRouteConfig, FlowEnvelope)]] = {
    val resTypeCfg = ctxt.cfg.resourceTypeConfigs.get(resType).get
    val fanout = DispatcherFanout(ctxt.cfg, ctxt.idSvc)
    fanout.funFanoutOutbound(envelope
      .withHeader(bs.headerResourceType, resType, true).get
      .withContextObject(bs.rtConfigKey, resTypeCfg)
    )
  }

  "The fanout subflow should" - {

    "create one FlowEnvelope per outbound config" in {

      withDispatcherConfig { ctxt =>

        val fanout = DispatcherFanout(ctxt.cfg, ctxt.idSvc)
        val envelope = FlowEnvelope(FlowMessage.noProps)

        performFanout(ctxt, fanout, "FanOut", envelope) match {
          case Success(s) =>
            s should have size 2
            assert(s.forall { case (outCfg, env) => env.id == envelope.id})
            val outIds = s.map(_._2.header[String](bs.headerOutboundId).get).distinct
            outIds should have size 2
            outIds should contain ("default")
            outIds should contain ("OtherApp")
          case Failure(t) => fail(t)
        }
      }
    }

    "create a workliststarted event for a configured resourceType" in {

      withDispatcherConfig { ctxt =>
        val fanout = DispatcherFanout(ctxt.cfg, ctxt.idSvc)

        ctxt.cfg.resourceTypeConfigs.keys.filter(_ != "NoOutbound").foreach { resType =>
          val envelope = FlowEnvelope(FlowMessage.noProps)

          val rtCfg = ctxt.cfg.resourceTypeConfigs.get(resType).get
          performFanout(ctxt, fanout, resType, envelope) match {
            case Success(s) =>
              val wl = fanout.toWorklist(s)
              wl.state should be (WorklistState.Started)
              wl.worklist.id should be (envelope.id)
              wl.worklist.items should have size (rtCfg.outbound.size)
            case Failure(t) =>
              bs.streamLogger.error(s"WorklistCreation failed for resource type [$resType]" )
              fail(t)
          }
        }
      }
    }

    "Generate exactly one worklist event and one envelope outbound config" in {

      def runnableFanout[T](
        source : Source[FlowEnvelope, T],
        fanout : DispatcherFanout
      )(implicit system : ActorSystem) = {

        val envColl = Collector[FlowEnvelope]("envelopes")
        val wlColl = Collector[WorklistEvent]("worklists")

        val sinkGraph : Graph[SinkShape[FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>
          import GraphDSL.Implicits._

          val fanoutGraph = b.add(fanout.build())

          val envOut = b.add(envColl.sink)
          val wlOut = b.add(wlColl.sink)

          fanoutGraph.out0 ~> envOut
          fanoutGraph.out1 ~>  wlOut

          SinkShape(fanoutGraph.in)
        }

        (envColl, wlColl, source.toMat(sinkGraph)(Keep.left))
      }

      withDispatcherConfig { ctxt =>
        implicit val system = ctxt.system
        implicit val materializer = ActorMaterializer()
        implicit val eCtxt = system.dispatcher

        val fanout = DispatcherFanout(ctxt.cfg, ctxt.idSvc)

        ctxt.cfg.resourceTypeConfigs.keys.filter(_ != "NoOutbound").foreach { resType =>
          val envelope = FlowEnvelope(FlowMessage.noProps)
          val rtCfg = ctxt.cfg.resourceTypeConfigs.get(resType).get

          val source = Source(List(envelope.withContextObject(bs.rtConfigKey, rtCfg)))
          val (envColl, wlColl, g) = runnableFanout(source, fanout)

          try {
            g.run()

            val envelopes = envColl.probe.expectMsgType[List[FlowEnvelope]](1.second)
            val worklists = wlColl.probe.expectMsgType[List[WorklistEvent]](1.second)

            bs.streamLogger.info(s"Testing resourcetype [$resType]")
            worklists should have size 1
            envelopes should have size rtCfg.outbound.size
          } finally  {
            system.stop(envColl.actor)
            system.stop(wlColl.actor)
          }
        }
      }
    }
  }
}
