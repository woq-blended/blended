package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.JmsQueue
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.MsgProperty.Implicits._
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.testsupport.StreamAssertions._
import blended.streams.testsupport.{Collector, StreamFactories}
import blended.streams.worklist.{WorklistEvent, WorklistStarted}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class CoreDispatcherSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport {

  override def country: String = "cc"
  override def location: String = "09999"
  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()
  override def loggerName: String = getClass().getName()

  implicit val bs : DispatcherBuilderSupport = new DispatcherBuilderSupport {
    override val prefix: String = "App"
    override val streamLogger: Logger = Logger(loggerName)
  }

  val defaultTimeout : FiniteDuration = 1.second

  val headerExistsFilter : String => FlowEnvelope => Boolean = key => env => env.flowMessage.header.isDefinedAt(key)
  val headerMissingFilter : String => FlowEnvelope => Boolean = key => env => !env.flowMessage.header.isDefinedAt(key)
  val headerFilter : String => AnyRef => FlowEnvelope => Boolean = key => value => env => env.header[AnyRef](key) == Some(value)
  def filterEnvelopes(envelopes : Seq[FlowEnvelope])(f : FlowEnvelope => Boolean) : Seq[FlowEnvelope] = envelopes.filter(f)

  case class DispatcherResult(
    out : Seq[FlowEnvelope],
    error : Seq[FlowEnvelope],
    worklist: Seq[WorklistEvent]
  )

  def runnableDispatcher (
    idSvc : ContainerIdentifierService,
    dispatcherCfg : ResourceTypeRouterConfig,
    bufferSize : Int
  )(implicit system: ActorSystem, materializer: Materializer) : (
    Collector[FlowEnvelope],
    Collector[WorklistEvent],
    Collector[FlowEnvelope],
    RunnableGraph[(ActorRef, KillSwitch)]
  )= {

    val jmsCollector = Collector[FlowEnvelope]("jms")
    val errCollector = Collector[FlowEnvelope]("error")
    val wlCollector = Collector[WorklistEvent]("worklist")

    val source : Source[FlowEnvelope, (ActorRef, KillSwitch)]
      = StreamFactories.keepAliveSource[FlowEnvelope](bufferSize)

    val sinkGraph : Graph[SinkShape[FlowEnvelope], NotUsed] = {
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val out : Inlet[FlowEnvelope] = builder.add(jmsCollector.sink).in
        val worklist : Inlet[WorklistEvent] = builder.add(wlCollector.sink).in
        val error : Inlet[FlowEnvelope] = builder.add(errCollector.sink).in

        val dispatcher = builder.add(DispatcherBuilder(idSvc, dispatcherCfg)(bs).core())

        dispatcher.out0 ~> out
        dispatcher.out1 ~> worklist
        dispatcher.out2 ~> error

        SinkShape(dispatcher.in)
      }
    }

    (jmsCollector, wlCollector, errCollector, source.toMat(sinkGraph)(Keep.left))
  }

  def withDispatcher(timeout : FiniteDuration, testMessages: FlowEnvelope*)(f: (DispatcherExecContext, DispatcherResult) => Unit): DispatcherResult = {

    def executeDispatcher(
      idSvc : ContainerIdentifierService,
      cfg: ResourceTypeRouterConfig,
      testMessages : FlowEnvelope*
    )(implicit system: ActorSystem, materializer : Materializer, timeout: FiniteDuration) : DispatcherResult = {

      val source = StreamFactories.keepAliveSource[FlowEnvelope](testMessages.size)
      val (jmsColl, wlColl, errorColl, g) = runnableDispatcher(idSvc, cfg, testMessages.size)

      try {
        val (actorRef, killswitch) = g.run()

        testMessages.foreach(m => actorRef ! m)

        implicit val eCtxt : ExecutionContext = system.dispatcher
        akka.pattern.after(timeout, system.scheduler)(Future {
          killswitch.shutdown()
        })

        val rOut = jmsColl.probe.expectMsgType[List[FlowEnvelope]](timeout + 500.millis)
        val rError = errorColl.probe.expectMsgType[List[FlowEnvelope]](timeout + 500.millis)
        val rWorklist = wlColl.probe.expectMsgType[List[WorklistStarted]](timeout + 500.millis)

        DispatcherResult(rOut, rError, rWorklist)
      } catch {
        case t : Throwable => throw t
      } finally {
        system.stop(jmsColl.actor)
        system.stop(wlColl.actor)
        system.stop(errorColl.actor)
      }
    }

    withDispatcherConfig{ ctxt =>

      implicit val system : ActorSystem = ctxt.system
      implicit val materializer : Materializer = ActorMaterializer()

      val result = executeDispatcher(ctxt.idSvc, ctxt.cfg, testMessages:_*)(system, materializer, timeout)

      try {
        f(ctxt, result)
        result
      } finally {

      }
    }
  }

  "The Core Dispatcher should" - {

    "add all configured default Headers" in {

      val props : FlowMessageProps = Map("ResourceType" -> "SagTest")
      val good = FlowEnvelope(FlowMessage("Normal", props))

      withDispatcher(defaultTimeout, good) { (_, result) =>
        result.out should have size 1
        result.out.head.header[String]("ComponentName") should be (Some("Dispatcher"))
        result.out.head.header[String]("ResourceType") should be (Some("SagTest"))
      }
    }

    "yield a MissingResourceType exception when the resourcetype is not set in the inbound message" in {

      val msg = FlowEnvelope(FlowMessage("Normal", FlowMessage.noProps))

      withDispatcher(defaultTimeout, msg) { (_, result) =>
        result.out should be (empty)
        result.worklist should be (empty)
        result.error should have size 1

        result.error.head.exception should be (defined)

        assert(result.error.head.exception.forall(t => t.isInstanceOf[MissingResourceType]))
      }

    }

    "yield an IllegalResourceType exception when the resourcetype given in the message is not configured" in {

      val props : FlowMessageProps = Map("ResourceType" -> "Dummy")
      val msg = FlowEnvelope(FlowMessage("Normal", props))

      withDispatcher(defaultTimeout, msg) { (_, result) =>
        result.out should be (empty)
        result.worklist should be (empty)
        result.error should have size 1

        result.error.head.exception should be (defined)

        assert(result.error.head.exception.forall(t => t.isInstanceOf[IllegalResourceType]))
      }
    }

    "yield an MissingOutboundConfig  exception when the resourcetype has no outbound blocks configured" in {

      val props : FlowMessageProps = Map("ResourceType" -> "NoOutbound")
      val msg = FlowEnvelope(FlowMessage("Normal", props))

      withDispatcher(defaultTimeout, msg) { (_, result) =>
        result.out should be (empty)
        result.worklist should be (empty)
        result.error should have size 1

        result.error.head.exception should be (defined)

        assert(result.error.head.exception.forall(t => t.isInstanceOf[MissingOutboundRouting]))
      }
    }

    "fanout for all out outbounds" in {
      val props : FlowMessageProps = Map("ResourceType" -> "FanOut")

      withDispatcher(3.seconds, FlowEnvelope(props)) { (_, result) =>
        result.out should have size 2
        result.worklist should have size 1
        result.worklist.head.worklist.id should be(result.out.head.id)
        result.worklist.head.worklist.items should have size 2

        val default = filterEnvelopes(result.out)(headerFilter(bs.headerOutboundId)("default"))
        default should have size 1

        verifyHeader(Map(
          "ResourceType" -> "FanOut",
          bs.headerBridgeVendor -> "sagum",
          bs.headerBridgeProvider -> "cc_queue",
          bs.headerBridgeDest -> JmsQueue("/Qucc/data/out").asString
        ), default.head.flowMessage.header) should be (empty)

        val other = filterEnvelopes(result.out)(headerFilter(bs.headerOutboundId)("OtherApp"))
        other should have size 1
        verifyHeader(Map(
          "ResourceType" -> "FanOut",
          bs.headerBridgeVendor -> "activemq",
          bs.headerBridgeProvider -> "activemq",
          bs.headerBridgeDest -> JmsQueue("OtherAppToQueue").asString,
          bs.headerTimeToLive -> 14400000L
        ), other.head.flowMessage.header) should be (empty)

      }
    }

    "correctly populate the Cbe headers if CBE is enabled on the resourcetype" in {

      val noCbe: FlowMessageProps = Map("ResourceType" -> "NoCbe")
      val withCbe : FlowMessageProps = Map("ResourceType" -> "WithCbe")

      withDispatcher(5.seconds, FlowEnvelope(noCbe), FlowEnvelope(withCbe)) { (cfg, result) =>
        result.out should have size 2

        val cbeOut = filterEnvelopes(result.out)(headerExistsFilter(bs.headerEventVendor))
        cbeOut should have size 1
        verifyHeader(Map(
          bs.headerCbeEnabled -> true,
          bs.headerEventVendor -> "sonic75",
          bs.headerEventProvider -> "central",
          bs.headerEventDest -> "queue:cc.global.evnt.out"
        ), cbeOut.head.flowMessage.header) should be (empty)

        val noCbeOut = filterEnvelopes(result.out)(headerMissingFilter(bs.headerEventVendor))
        noCbeOut should have size 1
        verifyHeader(Map(
          bs.headerCbeEnabled -> false,
        ), noCbeOut.head.flowMessage.header) should be (empty)

        result.worklist should have size 2
        result.error should be (empty)
      }
    }

    "evaluate conditional expressions to process outbound header" in {

      val propsInstore: FlowMessageProps = Map(
        "ResourceType" -> "Condition",
        "DestinationFileName" -> "TestFile",
        "InStoreCommunication" -> "1"
      )

      val propsCentral: FlowMessageProps = Map(
        "ResourceType" -> "Condition",
        "DestinationFileName" -> "TestFile",
        "InStoreCommunication" -> "0"
      )

      withDispatcher(5.seconds, FlowEnvelope(propsInstore), FlowEnvelope(propsCentral)) { (cfg, result) =>
        result.worklist should have size 2
        result.error should be (empty)

        result.out should have size 2

        val instore = filterEnvelopes(result.out)(headerFilter("InStoreCommunication")("1"))
        val central = filterEnvelopes(result.out)(headerFilter("InStoreCommunication")("0"))

        instore should have size 1
        verifyHeader(Map(
          "Description" -> "SalesDataFromScale",
          "DestinationName" -> "TestFile",
          bs.headerEventVendor -> "sonic75",
          bs.headerEventProvider -> "central",
          bs.headerEventDest -> "queue:cc.sib.global.data.out"
        ), instore.head.flowMessage.header)

        central should have size 1
        verifyHeader(Map(
          "Description" -> "SalesDataFromScale",
          "DestinationName" -> "TestFile",
          "Filename" -> "TestFile",
          "DestinationPath" -> "opt/inbound/",
          bs.headerEventVendor -> "activemq",
          bs.headerEventProvider -> "activemq",
          bs.headerEventDest -> "ClientToQ"
        ), central.head.flowMessage.header)

      }
    }
  }
}
