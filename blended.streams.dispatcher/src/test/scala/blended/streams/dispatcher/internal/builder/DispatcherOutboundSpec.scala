package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Source}
import akka.stream.{Graph, SinkShape}
import blended.jms.bridge.BridgeProviderConfig
import blended.jms.utils.JmsDestination
import blended.streams.dispatcher.internal.builder.DispatcherOutbound.DispatcherTarget
import blended.streams.jms.JmsFlowSupport
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.worklist._
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class DispatcherOutboundSpec extends DispatcherSpecSupport with Matchers {

  override def loggerName: String = classOf[DispatcherOutboundSpec].getName()

  private def runnableOutbound(
    ctxt: DispatcherExecContext,
    testMsg: FlowEnvelope,
    send: Flow[FlowEnvelope, FlowEnvelope, NotUsed]
  ): (Collector[WorklistEvent], Collector[FlowEnvelope], RunnableGraph[NotUsed]) = {

    implicit val system: ActorSystem = ctxt.system

    val outColl = Collector[WorklistEvent]("out")
    val errColl = Collector[FlowEnvelope]("err", onCollected = Some({ e: FlowEnvelope => e.acknowledge(); true }))

    val source = Source.single[FlowEnvelope](testMsg)

    val sinkGraph: Graph[SinkShape[FlowEnvelope], NotUsed] = {
      GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val outStep = b.add(DispatcherBuilder(ctxt.ctCtxt, ctxt.cfg, send, ctxt.envLogger)(ctxt.bs).outbound())
        val out = b.add(outColl.sink)
        val err = b.add(errColl.sink)

        outStep.out0 ~> out
        outStep.out1 ~> err

        SinkShape(outStep.in)
      }
    }

    (outColl, errColl, source.to(sinkGraph))
  }

  def testOutbound(expectedState: WorklistState, send: Flow[FlowEnvelope, FlowEnvelope, NotUsed]): Unit = {
    withDispatcherConfig(registry) { ctxt =>
      implicit val system: ActorSystem = ctxt.system
      implicit val eCtxt: ExecutionContext = system.dispatcher

      val envelope = FlowEnvelope().withHeader(ctxt.bs.headerConfig.headerBranch, "outbound").get

      val (outColl, errColl, out) = runnableOutbound(ctxt, envelope, send)

      try {
        out.run()

        val result = for {
          err <- errColl.result
          evt <- outColl.result
        } yield (err, evt)

        result.map {
          case (error, events) =>
            error should be(empty)
            events should have size 1

            val event = events.head
            event.worklist.items should have size 1
            event.worklist.id should be(envelope.id)
            event.state should be(expectedState)
        }
      } finally {
        system.stop(outColl.actor)
        system.stop(errColl.actor)
      }
    }
  }

  "The outbound flow of the dispatcher should" - {

    "produce a worklist completed event for successfull completions of the outbound flow" in {
      val good = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env => env }
      testOutbound(WorklistStateCompleted, good)
    }

    "produce a worklist failed event after unsuccessfull completions of the outbound flow" in {
      val bad = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env => env.withException(new Exception("Boom !")) }
      testOutbound(WorklistStateFailed, bad)
    }
  }

  "The outbound routing decider should" - {

    val provider = BridgeProviderConfig(
      vendor = "sonic75",
      provider = "central",
      internal = false,
      inbound = JmsDestination.create("in").get,
      outbound = JmsDestination.create("out").get,
      errors = JmsDestination.create("error").get,
      transactions = JmsDestination.create("trans").get,
      cbes = JmsDestination.create("cbes").get,
      retry = None,
      retryFailed = JmsDestination.create("error").get,
      ackTimeout = 1.second
    )

    val prefix: DispatcherExecContext => String = ctxt => ctxt.bs.headerConfig.prefix
    val srcVendorHeader: DispatcherExecContext => String = ctxt => JmsFlowSupport.srcVendorHeader(prefix(ctxt))
    val srcProviderHeader: DispatcherExecContext => String = ctxt => JmsFlowSupport.srcProviderHeader(prefix(ctxt))
    val replyToHeader: DispatcherExecContext => String = ctxt => JmsFlowSupport.replyToHeader(prefix(ctxt))
    val srcDestHeader: DispatcherExecContext => String = ctxt => JmsFlowSupport.srcDestHeader(prefix(ctxt))

    "resolve a replyTo destination if no outbound destination is set in resource type router" in {

      withDispatcherConfig(registry) { ctxt =>
        val env: FlowEnvelope = FlowEnvelope(
          FlowMessage(FlowMessage.noProps)
        ).withHeader(srcVendorHeader(ctxt), "activemq")
          .get
          .withHeader(srcProviderHeader(ctxt), "activemq")
          .get
          .withHeader(replyToHeader(ctxt), "response")
          .get
          .withHeader(srcDestHeader(ctxt), JmsDestination.create("Dummy").get.asString)
          .get
          .withContextObject(ctxt.bs.bridgeProviderKey, provider)
          // This will trigger the replyto routing
          .withContextObject(ctxt.bs.bridgeDestinationKey, None)

        val routing: DispatcherTarget = DispatcherOutbound
          .outboundRouting(
            dispatcherCfg = ctxt.cfg,
            ctCtxt = ctxt.ctCtxt,
            bs = ctxt.bs,
            streamLogger = ctxt.envLogger
          )(env)
          .get

        routing should be(DispatcherTarget("activemq", "activemq", JmsDestination.create("response").get))
      }
    }

    "resolve a replyTo destination if the outbound destination is set to 'replyTo' in the config" in {

      withDispatcherConfig(registry) { ctxt =>
        val env: FlowEnvelope = FlowEnvelope(
          FlowMessage(FlowMessage.noProps)
        ).withHeader(srcVendorHeader(ctxt), "activemq")
          .get
          .withHeader(srcProviderHeader(ctxt), "activemq")
          .get
          .withHeader(replyToHeader(ctxt), "response")
          .get
          .withHeader(srcDestHeader(ctxt), JmsDestination.create("Dummy").get.asString)
          .get
          .withContextObject(ctxt.bs.bridgeProviderKey, provider)
          // This will trigger the replyto routing
          .withContextObject(ctxt.bs.bridgeDestinationKey, Some(JmsFlowSupport.replyToQueueName))

        val routing: DispatcherTarget = DispatcherOutbound
          .outboundRouting(
            dispatcherCfg = ctxt.cfg,
            ctCtxt = ctxt.ctCtxt,
            bs = ctxt.bs,
            streamLogger = ctxt.envLogger
          )(env)
          .get

        routing should be(DispatcherTarget("activemq", "activemq", JmsDestination.create("response").get))
      }
    }

    "resolve to the configured target destination" in {
      withDispatcherConfig(registry) { ctxt =>
        val env: FlowEnvelope = FlowEnvelope(
          FlowMessage(FlowMessage.noProps)
        ).withHeader(srcVendorHeader(ctxt), "activemq")
          .get
          .withHeader(srcProviderHeader(ctxt), "activemq")
          .get
          .withHeader(replyToHeader(ctxt), "response")
          .get
          .withHeader(srcDestHeader(ctxt), JmsDestination.create("Dummy").get.asString)
          .get
          .withContextObject(ctxt.bs.bridgeProviderKey, provider)
          .withContextObject(ctxt.bs.bridgeDestinationKey, Some("centralDest"))

        val routing: DispatcherTarget = DispatcherOutbound
          .outboundRouting(
            dispatcherCfg = ctxt.cfg,
            ctCtxt = ctxt.ctCtxt,
            bs = ctxt.bs,
            streamLogger = ctxt.envLogger
          )(env)
          .get

        routing should be(
          DispatcherTarget(provider.vendor, provider.provider, JmsDestination.create("centralDest").get)
        )
      }
    }
  }
}
