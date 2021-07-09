package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Source}
import blended.streams.dispatcher.internal.OutboundRouteConfig
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.worklist.{WorklistEvent, WorklistStateStarted}
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class FanoutSpec extends DispatcherSpecSupport with Matchers {

  override def loggerName: String = classOf[FanoutSpec].getName()

  def performFanout(
    ctxt: DispatcherExecContext,
    fanout: DispatcherFanout,
    resType: String,
    envelope: FlowEnvelope
  ): Try[Seq[(OutboundRouteConfig, FlowEnvelope)]] = {
    val resTypeCfg = ctxt.cfg.resourceTypeConfigs(resType)
    val fanout = DispatcherFanout(ctxt.cfg, ctxt.ctCtxt, ctxt.envLogger)(ctxt.bs)
    fanout.funFanoutOutbound(
      envelope
        .withHeader(ctxt.bs.headerResourceType, resType)
        .get
        .withContextObject(ctxt.bs.rtConfigKey, resTypeCfg)
    )
  }

  "The fanout subflow should" - {

    "create one FlowEnvelope per outbound config" in {

      withDispatcherConfig(registry) { ctxt =>
        val fanout = DispatcherFanout(ctxt.cfg, ctxt.ctCtxt, ctxt.envLogger)(ctxt.bs)
        val envelope = FlowEnvelope(FlowMessage.noProps)

        performFanout(ctxt, fanout, "FanOut", envelope) match {
          case Success(s) =>
            s should have size 2
            assert(s.forall { case (_, env) => env.id == envelope.id })
            val outIds = s.map(_._2.header[String](ctxt.bs.headerConfig.headerBranch).get).distinct
            outIds should have size 2
            outIds should contain("default")
            outIds should contain("OtherApp")
          case Failure(t) => fail(t)
        }
      }
    }

    "create a workliststarted event for a configured resourceType" in {

      withDispatcherConfig(registry) { ctxt =>
        val fanout = DispatcherFanout(ctxt.cfg, ctxt.ctCtxt, ctxt.envLogger)(ctxt.bs)

        ctxt.cfg.resourceTypeConfigs.keys.filter(_ != "NoOutbound").foreach { resType =>
          val envelope = FlowEnvelope(FlowMessage.noProps)

          val rtCfg = ctxt.cfg.resourceTypeConfigs(resType)
          performFanout(ctxt, fanout, resType, envelope) match {
            case Success(s) =>
              val wl = fanout.toWorklist(s)
              wl.state should be(WorklistStateStarted)
              wl.worklist.id should be(envelope.id)
              wl.worklist.items should have size (rtCfg.outbound.size)
            case Failure(t) =>
              ctxt.envLogger.underlying.error(s"WorklistCreation failed for resource type [$resType]")
              fail(t)
          }
        }
      }
    }

    "Generate exactly one worklist event and one envelope outbound config" in {

      def runnableFanout[T](
        source: Source[FlowEnvelope, T],
        fanout: DispatcherFanout
      )(implicit system: ActorSystem): (
        Collector[FlowEnvelope],
        Collector[WorklistEvent],
        RunnableGraph[T]
      ) = {

        val envColl: Collector[FlowEnvelope] =
          Collector[FlowEnvelope](name = "envelopes", onCollected = Some({ e: FlowEnvelope => e.acknowledge(); true }))

        val wlColl: Collector[WorklistEvent] = Collector[WorklistEvent]("worklists")

        val sinkGraph: Graph[SinkShape[FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>
          import GraphDSL.Implicits._

          val fanoutGraph = b.add(fanout.build())

          val envOut = b.add(envColl.sink)
          val wlOut = b.add(wlColl.sink)

          fanoutGraph.out0 ~> envOut
          fanoutGraph.out1 ~> wlOut

          SinkShape(fanoutGraph.in)
        }

        (envColl, wlColl, source.toMat(sinkGraph)(Keep.left))
      }

      withDispatcherConfig(registry) { ctxt =>
        implicit val system: ActorSystem = ctxt.system
        implicit val eCtxt: ExecutionContext = system.dispatcher

        val fanout = DispatcherFanout(ctxt.cfg, ctxt.ctCtxt, ctxt.envLogger)(ctxt.bs)

        ctxt.cfg.resourceTypeConfigs.keys.filter(_ != "NoOutbound").foreach { resType =>
          val envelope = FlowEnvelope(FlowMessage.noProps)
          val rtCfg = ctxt.cfg.resourceTypeConfigs(resType)

          val source = Source(List(envelope.withContextObject(ctxt.bs.rtConfigKey, rtCfg)))
          val (envColl, wlColl, g) = runnableFanout(source, fanout)

          try {
            g.run()

            val result = for {
              env <- envColl.result
              wl <- wlColl.result
            } yield (env, wl)

            result.map {
              case (envelopes, worklists) =>
                ctxt.envLogger.underlying.info(s"Testing resourcetype [$resType]")
                worklists should have size 1
                envelopes should have size rtCfg.outbound.size
            }
          } finally {
            system.stop(envColl.actor)
            system.stop(wlColl.actor)
          }
        }
      }
    }
  }
}
