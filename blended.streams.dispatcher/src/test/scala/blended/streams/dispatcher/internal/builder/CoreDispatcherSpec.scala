package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Source}
import akka.testkit.TestProbe
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.JmsQueue
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.MsgProperty.Implicits._
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.testsupport.StreamAssertions._
import blended.streams.testsupport.StreamFactories
import blended.streams.worklist.{WorklistEvent, WorklistStarted}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.Future
import scala.concurrent.duration._

class CoreDispatcherSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport {

  override def country: String = "cc"
  override def location: String = "09999"
  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()
  override def loggerName: String = getClass().getName()

  implicit val bs = new DispatcherBuilderSupport {
    override val prefix: String = "SIB"
    override val streamLogger: Logger = Logger(loggerName)
  }

  val defaultTimeout = 1.second

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
    TestProbe,
    TestProbe,
    TestProbe,
    RunnableGraph[(ActorRef, KillSwitch)]
  )= {

    val (jmsProbe, jmsSink) = collector[FlowEnvelope]("jms")
    val (errorProbe, errorSink) = collector[FlowEnvelope]("error")
    val (wlProbe, worklistSink) = collector[WorklistEvent]("worklist")

    val source : Source[FlowEnvelope, (ActorRef, KillSwitch)]
      = StreamFactories.keepAliveSource[FlowEnvelope](bufferSize)

    val sinkGraph : Graph[SinkShape[FlowEnvelope], NotUsed] = {
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val out : Inlet[FlowEnvelope] = builder.add(jmsSink).in
        val worklist : Inlet[WorklistEvent] = builder.add(worklistSink).in
        val error : Inlet[FlowEnvelope] = builder.add(errorSink).in

        val devNull = Inlet[FlowEnvelope]("devNull")

        val dispatcher = builder.add(DispatcherBuilder(idSvc, dispatcherCfg)(bs).core())

        dispatcher.out0 ~> out
        dispatcher.out1 ~> worklist
        dispatcher.out2 ~> error

        SinkShape(dispatcher.in)
      }
    }

    (jmsProbe, wlProbe, errorProbe, source.toMat(sinkGraph)(Keep.left))
  }

  def withDispatcher(timeout : FiniteDuration, testMessages: FlowEnvelope*)(f: (DispatcherExecContext, DispatcherResult) => Unit): DispatcherResult = {

    def executeDispatcher(
      idSvc : ContainerIdentifierService,
      cfg: ResourceTypeRouterConfig,
      testMessages : FlowEnvelope*
    )(implicit system: ActorSystem, materializer : Materializer, timeout: FiniteDuration) : DispatcherResult = {

      val source = StreamFactories.keepAliveSource[FlowEnvelope](testMessages.size)

      val (jmsProbe, wlProbe, errorProbe, g) = runnableDispatcher(idSvc, cfg, testMessages.size)
      val (actorRef, killswitch) = g.run()

      testMessages.foreach(m => actorRef ! m)

      implicit val eCtxt = system.dispatcher
      akka.pattern.after(timeout, system.scheduler)(Future { killswitch.shutdown() })

      val rOut = jmsProbe.expectMsgType[List[FlowEnvelope]](timeout + 500.millis)
      val rError = errorProbe.expectMsgType[List[FlowEnvelope]](timeout + 500.millis)
      val rWorklist = wlProbe.expectMsgType[List[WorklistStarted]](timeout + 500.millis)

      DispatcherResult(rOut, rError, rWorklist)
    }

    withDispatcherConfig(){ ctxt =>

      implicit val system = ctxt.system
      implicit val materializer = ActorMaterializer()

      val result = executeDispatcher(ctxt.idSvc, ctxt.cfg, testMessages:_*)(system, materializer, timeout)
      f(ctxt, result)

      result
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
      val props : FlowMessageProps = Map("ResourceType" -> "KPosData")

      withDispatcher(3.seconds, FlowEnvelope(props)) { (_, result) =>
        result.out should have size 2
        result.worklist should have size 1
        result.worklist.head.worklist.id should be(result.out.head.id)
        result.worklist.head.worklist.items should have size 2

        val default = filterEnvelopes(result.out)(headerFilter(bs.headerOutboundId)("default"))
        default should have size 1

        verifyHeader(Map(
          "ResourceType" -> "KPosData",
          bs.headerBridgeVendor -> "sagum",
          bs.headerBridgeProvider -> "cc_queue",
          bs.headerBridgeDest -> JmsQueue("/Qucc/sib/kpos/data/out").asString
        ), default.head.flowMessage.header) should be (empty)

        val vitra = filterEnvelopes(result.out)(headerFilter(bs.headerOutboundId)("VitraCom"))
        vitra should have size 1
        verifyHeader(Map(
          "ResourceType" -> "KPosData",
          bs.headerBridgeVendor -> "activemq",
          bs.headerBridgeProvider -> "activemq",
          bs.headerBridgeDest -> JmsQueue("VitracomClientToQueue").asString,
          bs.headerTimeToLive -> 14400000L
        ), vitra.head.flowMessage.header) should be (empty)

      }
    }

    "correctly populate the Cbe headers if CBE is enabled on the resourcetype" in {

      val noCbe: FlowMessageProps = Map("ResourceType" -> "ShopRegister")
      val withCbe : FlowMessageProps = Map("ResourceType" -> "Msg2TopicScaleAssortment")

      withDispatcher(5.seconds, FlowEnvelope(noCbe), FlowEnvelope(withCbe)) { (cfg, result) =>
        result.out should have size 2

        val cbeOut = filterEnvelopes(result.out)(headerExistsFilter(bs.headerEventVendor))
        cbeOut should have size 1
        verifyHeader(Map(
          bs.headerCbeEnabled -> true,
          bs.headerEventVendor -> "sonic75",
          bs.headerEventProvider -> "central",
          bs.headerEventDest -> "queue:cc.sib.global.evnt.out"
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
        "ResourceType" -> "SalesDataFromScale",
        "DestinationFileName" -> "TestFile",
        "InStoreCommunication" -> "1"
      )

      val propsCentral: FlowMessageProps = Map(
        "ResourceType" -> "SalesDataFromScale",
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
          "DestinationPath" -> "C:/Scale/Inbound/",
          bs.headerEventVendor -> "activemq",
          bs.headerEventProvider -> "activemq",
          bs.headerEventDest -> "XPDinteg_PosClientToQ"
        ), central.head.flowMessage.header)

      }
    }
  }
}
