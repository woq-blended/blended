package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Source}
import blended.jms.utils.JmsQueue
import blended.streams.StreamFactories
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.{CollectingActor, Collector}
import blended.streams.testsupport.StreamAssertions._
import blended.streams.worklist.WorklistEvent
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class CoreDispatcherSpec extends DispatcherSpecSupport with Matchers {

  override def loggerName: String = classOf[CoreDispatcherSpec].getName()

  val goodFlow: Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
    Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env => env }

  val defaultTimeout: FiniteDuration = 1.second

  val headerExistsFilter: String => FlowEnvelope => Boolean = key => env => env.flowMessage.header.isDefinedAt(key)
  val headerMissingFilter: String => FlowEnvelope => Boolean = key => env => !env.flowMessage.header.isDefinedAt(key)
  def headerFilter[T: Manifest](key: String, expected: T)(env: FlowEnvelope): Boolean =
    env.header[T](key).contains(expected)

  def filterEnvelopes(envelopes: Seq[FlowEnvelope])(f: FlowEnvelope => Boolean): Seq[FlowEnvelope] = envelopes.filter(f)

  case class DispatcherResult(
    out: List[FlowEnvelope],
    error: List[FlowEnvelope],
    worklist: List[WorklistEvent]
  )

  def runnableDispatcher(
    ctxt: DispatcherExecContext,
    bufferSize: Int
  )(implicit system: ActorSystem): (
    Collector[FlowEnvelope],
    Collector[WorklistEvent],
    Collector[FlowEnvelope],
    RunnableGraph[(ActorRef, KillSwitch)]
  ) = {
    val ack: FlowEnvelope => Boolean = { e => e.acknowledge(); true }

    val jmsCollector = Collector[FlowEnvelope]("jms", onCollected = Some(ack))
    val errCollector = Collector[FlowEnvelope]("error", onCollected = Some(ack))
    val wlCollector = Collector[WorklistEvent]("worklist")

    val source: Source[FlowEnvelope, (ActorRef, KillSwitch)] = StreamFactories.keepAliveSource[FlowEnvelope](bufferSize)

    val sinkGraph: Graph[SinkShape[FlowEnvelope], NotUsed] = {
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val out: Inlet[FlowEnvelope] = builder.add(jmsCollector.sink).in
        val worklist: Inlet[WorklistEvent] = builder.add(wlCollector.sink).in
        val error: Inlet[FlowEnvelope] = builder.add(errCollector.sink).in

        val dispatcher = builder.add(DispatcherBuilder(ctxt.ctCtxt, ctxt.cfg, goodFlow, ctxt.envLogger)(ctxt.bs).core())

        dispatcher.out0 ~> out
        dispatcher.out1 ~> worklist
        dispatcher.out2 ~> error

        SinkShape(dispatcher.in)
      }
    }

    (jmsCollector, wlCollector, errCollector, source.toMat(sinkGraph)(Keep.left))
  }

  def withDispatcher(
    timeout: FiniteDuration,
    testMessages: FlowEnvelope*
  )(
    f: (DispatcherExecContext, DispatcherResult) => Unit
  ): DispatcherResult = {

    def executeDispatcher(
      ctxt: DispatcherExecContext,
      timeout: FiniteDuration,
      testMessages: FlowEnvelope*
    )(implicit system: ActorSystem): Future[DispatcherResult] = {

      StreamFactories.keepAliveSource[FlowEnvelope](testMessages.size)
      val (jmsColl, wlColl, errorColl, g) = runnableDispatcher(ctxt, testMessages.size)

      try {
        val (actorRef, killswitch) = g.run()

        testMessages.foreach { m =>
          actorRef ! m
        }

        implicit val eCtxt: ExecutionContext = system.dispatcher
        akka.pattern.after(timeout, system.scheduler)(Future {
          jmsColl.actor ! CollectingActor.Success
          errorColl.actor ! CollectingActor.Success
          wlColl.actor ! CollectingActor.Success
          killswitch.shutdown()
        })

        for {
          jmsResult <- jmsColl.result
          errResult <- errorColl.result
          wlResult <- wlColl.result
        } yield {
          val res = DispatcherResult(jmsResult, errResult, wlResult)
          //println(res)
          res
        }
      } catch {
        case t: Throwable => throw t
      }
    }

    withDispatcherConfig(registry) { ctxt =>
      implicit val system: ActorSystem = ctxt.system
      implicit val eCtxt: ExecutionContext = system.dispatcher

      Await.result(
        executeDispatcher(ctxt, timeout + 100.millis, testMessages: _*)
          .map { r =>
            f(ctxt, r)
            r
          },
        timeout + 1.second
      )
    }
  }

  "The Core Dispatcher should" - {

    "add all configured default Headers" in {

      val props: FlowMessageProps = FlowMessage.props("ResourceType" -> "SagTest").get
      val good = FlowEnvelope(FlowMessage("Normal")(props))

      withDispatcher(defaultTimeout, good) { (_, result) =>
        result.out should have size 1
        result.out.head.header[String]("ComponentName") should be(Some("Dispatcher"))
        result.out.head.header[String]("ResourceType") should be(Some("SagTest"))
      }
    }

    "yield a MissingResourceType exception when the resourcetype is not set in the inbound message" in {

      val msg = FlowEnvelope(FlowMessage("Normal")(FlowMessage.noProps))

      withDispatcher(defaultTimeout, msg) { (_, result) =>
        result.out should be(empty)
        result.worklist should be(empty)
        result.error should have size 1

        result.error.head.exception should be(defined)

        assert(result.error.head.exception.forall(t => t.isInstanceOf[MissingResourceType]))
      }

    }

    "yield an IllegalResourceType exception when the resourcetype given in the message is not configured" in {

      val props: FlowMessageProps = FlowMessage.props("ResourceType" -> "Dummy").get
      val msg = FlowEnvelope(FlowMessage("Normal")(props))

      withDispatcher(defaultTimeout, msg) { (_, result) =>
        result.out should be(empty)
        result.worklist should be(empty)
        result.error should have size 1

        result.error.head.exception should be(defined)

        assert(result.error.head.exception.forall(t => t.isInstanceOf[IllegalResourceType]))
      }
    }

    "yield an MissingOutboundConfig  exception when the resourcetype has no outbound blocks configured" in {

      val props: FlowMessageProps = FlowMessage.props("ResourceType" -> "NoOutbound").get
      val msg = FlowEnvelope(FlowMessage("Normal")(props))

      withDispatcher(defaultTimeout, msg) { (_, result) =>
        result.out should be(empty)
        result.worklist should be(empty)
        result.error should have size 1

        result.error.head.exception should be(defined)

        assert(result.error.head.exception.forall(t => t.isInstanceOf[MissingOutboundRouting]))
      }
    }

    "fanout for all out outbounds" in {
      val props: FlowMessageProps = FlowMessage.props("ResourceType" -> "FanOut").get

      withDispatcher(3.seconds, FlowEnvelope(props)) { (ctxt, result) =>
        result.out should have size 2
        result.worklist should have size 1
        result.worklist.head.worklist.id should be(result.out.head.id)
        result.worklist.head.worklist.items should have size 2

        val default = filterEnvelopes(result.out)(headerFilter[String](ctxt.bs.headerConfig.headerBranch, "default"))
        default should have size 1

        verifyHeader(
          FlowMessage
            .props(
              "ResourceType" -> "FanOut",
              ctxt.bs.headerBridgeVendor -> "sagum",
              ctxt.bs.headerBridgeProvider -> "cc_queue",
              ctxt.bs.headerBridgeDest -> JmsQueue("/Qucc/data/out").asString
            )
            .get,
          default.head.flowMessage.header
        ) should be(empty)

        val other = filterEnvelopes(result.out)(headerFilter[String](ctxt.bs.headerConfig.headerBranch, "OtherApp"))
        other should have size 1
        verifyHeader(
          FlowMessage
            .props(
              "ResourceType" -> "FanOut",
              ctxt.bs.headerBridgeVendor -> "activemq",
              ctxt.bs.headerBridgeProvider -> "activemq",
              ctxt.bs.headerBridgeDest -> JmsQueue("OtherAppToQueue").asString,
              ctxt.bs.headerTimeToLive -> 14400000L
            )
            .get,
          other.head.flowMessage.header
        ) should be(empty)

      }
    }

    "correctly populate the Cbe headers if CBE is enabled on the resourcetype" in {

      val noCbe: FlowMessageProps = FlowMessage.props("ResourceType" -> "NoCbe").get
      val withCbe: FlowMessageProps = FlowMessage.props("ResourceType" -> "WithCbe").get

      withDispatcher(5.seconds, FlowEnvelope(noCbe), FlowEnvelope(withCbe)) { (ctxt, result) =>
        result.out should have size 2

        val cbeOut = filterEnvelopes(result.out)(headerExistsFilter(ctxt.bs.headerEventVendor))
        cbeOut should have size 1
        verifyHeader(
          FlowMessage
            .props(
              ctxt.bs.headerCbeEnabled -> true,
              ctxt.bs.headerEventVendor -> "sonic75",
              ctxt.bs.headerEventProvider -> "central",
              ctxt.bs.headerEventDest -> "cc.global.evnt.out"
            )
            .get,
          cbeOut.head.flowMessage.header
        ) should be(empty)

        val noCbeOut = filterEnvelopes(result.out)(headerMissingFilter(ctxt.bs.headerEventVendor))
        noCbeOut should have size 1
        verifyHeader(
          FlowMessage
            .props(
              ctxt.bs.headerCbeEnabled -> false
            )
            .get,
          noCbeOut.head.flowMessage.header
        ) should be(empty)

        result.worklist should have size 2
        result.error should be(empty)
      }
    }

    "evaluate conditional expressions to process outbound header" in {

      val propsInstore: FlowMessageProps = FlowMessage
        .props(
          "ResourceType" -> "Condition",
          "DestinationFileName" -> "TestFile",
          "InStoreCommunication" -> "1"
        )
        .get

      val propsCentral: FlowMessageProps = FlowMessage
        .props(
          "ResourceType" -> "Condition",
          "DestinationFileName" -> "TestFile",
          "InStoreCommunication" -> "0"
        )
        .get

      withDispatcher(5.seconds, FlowEnvelope(propsInstore), FlowEnvelope(propsCentral)) { (ctxt, result) =>
        result.worklist should have size 2
        result.error should be(empty)

        result.out should have size 2

        val instore = filterEnvelopes(result.out)(headerFilter[String]("InStoreCommunication", "1"))
        val central = filterEnvelopes(result.out)(headerFilter[String]("InStoreCommunication", "0"))

        instore should have size 1
        verifyHeader(
          FlowMessage
            .props(
              "Description" -> "Condition",
              "DestinationName" -> "TestFile",
              "FileName" -> "TestFile",
              "DestinationPath" -> "/opt/inbound"
            )
            .get,
          instore.head.flowMessage.header
        )

        central should have size 1
        verifyHeader(
          FlowMessage
            .props(
              "Description" -> "Condition",
              "DestinationName" -> "TestFile"
            )
            .get,
          central.head.flowMessage.header
        ) should be(empty)
      }
    }

    "evaluate dynamic destinations correctly" in {

      val destName = "dynamicQueue"

      val propsDynamic: FlowMessageProps = FlowMessage
        .props(
          "ResourceType" -> "Dynamic",
          "targetDest" -> destName
        )
        .get

      withDispatcher(5.seconds, FlowEnvelope(propsDynamic)) { (ctxt, result) =>
        println(result.out)

        result.worklist should have size 1
        result.error should be(empty)

        result.out should have size 1

        verifyHeader(
          FlowMessage
            .props(
              ctxt.bs.headerBridgeVendor -> "sagum",
              ctxt.bs.headerBridgeProvider -> "cc_queue",
              ctxt.bs.headerBridgeDest -> destName
            )
            .get,
          result.out.head.flowMessage.header
        ) should be(empty)
      }
    }
  }
}
