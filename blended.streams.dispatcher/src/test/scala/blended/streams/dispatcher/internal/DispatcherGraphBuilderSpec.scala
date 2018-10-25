package blended.streams.dispatcher.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.utils.JmsQueue
import blended.streams.testsupport.StreamAssertions._
import blended.streams.dispatcher.internal.builder.{DispatcherBuilderSupport, IllegalResourceType, MissingOutboundRouting, MissingResourceType}
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.MsgProperty.Implicits._
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.{LoggingFreeSpec, LoggingFreeSpecLike}
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.reflect.ClassTag

class DispatcherGraphBuilderSpec extends LoggingFreeSpec
  with LoggingFreeSpecLike
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val log = Logger[DispatcherGraphBuilderSpec]
  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  private implicit val bs : DispatcherBuilderSupport = new DispatcherBuilderSupport {
    override val prefix: String = "SIB"
    override val streamLogger: Logger = log
  }

  def withDispatcher(testMessages: FlowEnvelope*)(f : (ResourceTypeRouterConfig, DispatcherResult) => Unit) : DispatcherResult = {

    System.setProperty("SIBCountry", "cc")
    System.setProperty("SIBLocation", "09999")

    withSimpleBlendedContainer(baseDir) { sr =>
      withStartedBundles(sr)(Seq(
        "blended.akka" -> Some(() => new BlendedAkkaActivator()),
        "blended.jms.bridge" -> Some(() => new BridgeActivator())
      )) { sr =>

        try {
          val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](sr)(None)(
            clazz = ClassTag(classOf[ContainerIdentifierService]),
            timeout = 3.seconds
          )

          implicit val system : ActorSystem = mandatoryService[ActorSystem](sr)(None)(
            clazz = ClassTag(classOf[ActorSystem]),
            timeout = 3.seconds
          )

          val provider : BridgeProviderRegistry = mandatoryService[BridgeProviderRegistry](sr)(None)(
            clazz = ClassTag(classOf[BridgeProviderRegistry]),
            timeout = 3.seconds
          )

          implicit val eCtxt : ExecutionContext = system.dispatcher
          implicit val materializer : ActorMaterializer = ActorMaterializer()

          val cfg = ResourceTypeRouterConfig.create(
            idSvc,
            provider,
            idSvc.containerContext.getContainerConfig().getConfig("blended.streams.dispatcher")
          ).get

          val result = DispatcherExecutor.execute(system, idSvc, cfg, testMessages:_*)(materializer, 10.seconds)

          f(cfg, result)

          result
        } catch {
          case t: Throwable => fail(t)
        }
      }
    }
  }

  val headerExistsFilter : String => FlowEnvelope => Boolean = key => env => env.flowMessage.header.isDefinedAt(key)
  val headerMissingFilter : String => FlowEnvelope => Boolean = key => env => !env.flowMessage.header.isDefinedAt(key)
  val headerFilter : String => AnyRef => FlowEnvelope => Boolean = key => value => env => env.header[AnyRef](key) == Some(value)
  def filterEnvelopes(envelopes : Seq[FlowEnvelope])(f : FlowEnvelope => Boolean) : Seq[FlowEnvelope] = envelopes.filter(f)

  "The Dispatcher RouteBuilder should" - {

    "add all configured default Headers" in {

      val props : FlowMessageProps = Map("ResourceType" -> "SagTest")
      val good = FlowEnvelope(FlowMessage("Normal", props))

      withDispatcher(good) { (cfg, result) =>
        result.out should have size 1
        result.out.head.header[String]("ComponentName") should be (Some("Dispatcher"))
        result.out.head.header[String]("ResourceType") should be (Some("SagTest"))
      }
    }

    "yield a MissingResourceType exception when the resourcetype is not set in the inbound message" in {

      val msg = FlowEnvelope(FlowMessage("Normal", FlowMessage.noProps))

      withDispatcher(msg) { (cfg, result) =>
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

      withDispatcher(msg) { (cfg, result) =>
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

      withDispatcher(msg) { (cfg, result) =>
        result.out should be (empty)
        result.worklist should be (empty)
        result.error should have size 1

        result.error.head.exception should be (defined)

        assert(result.error.head.exception.forall(t => t.isInstanceOf[MissingOutboundRouting]))
      }
    }

    "fanout for all out outbounds" in {
      val props : FlowMessageProps = Map("ResourceType" -> "KPosData")

      withDispatcher(FlowEnvelope(props)) { (cfg, result) =>
        result.out should have size 2
        result.worklist should have size 1
        result.worklist.head.worklist.id should be(result.out.head.id)
        result.worklist.head.worklist.items should have size 2

        val default = filterEnvelopes(result.out)(headerFilter(bs.HEADER_OUTBOUND_ID)("default"))
        default should have size 1

        verifyHeader(Map(
          "ResourceType" -> "KPosData",
          bs.HEADER_BRIDGE_VENDOR -> "sagum",
          bs.HEADER_BRIDGE_PROVIDER -> "cc_queue",
          bs.HEADER_BRIDGE_DEST -> JmsQueue("/Qucc/sib/kpos/data/out").asString
        ), default.head.flowMessage.header) should be (empty)

        val vitra = filterEnvelopes(result.out)(headerFilter(bs.HEADER_OUTBOUND_ID)("VitraCom"))
        vitra should have size 1
        verifyHeader(Map(
          "ResourceType" -> "KPosData",
          bs.HEADER_BRIDGE_VENDOR -> "activemq",
          bs.HEADER_BRIDGE_PROVIDER -> "activemq",
          bs.HEADER_BRIDGE_DEST -> JmsQueue("VitracomClientToQueue").asString,
          bs.HEADER_TIMETOLIVE -> 14400000L
        ), vitra.head.flowMessage.header) should be (empty)

      }
    }

    "correctly populate the Cbe headers if CBE is enabled on the resourcetype" in {

      val noCbe: FlowMessageProps = Map("ResourceType" -> "ShopRegister")
      val withCbe : FlowMessageProps = Map("ResourceType" -> "Msg2TopicScaleAssortment")

      withDispatcher(FlowEnvelope(noCbe), FlowEnvelope(withCbe)) { (cfg, result) =>
        result.out should have size 2

        val cbeOut = filterEnvelopes(result.out)(headerExistsFilter(bs.HEADER_EVENT_VENDOR))
        cbeOut should have size 1
        verifyHeader(Map(
          bs.HEADER_CBE_ENABLED -> true,
          bs.HEADER_EVENT_VENDOR -> "sonic75",
          bs.HEADER_EVENT_PROVIDER -> "central",
          bs.HEADER_EVENT_DEST -> "queue:cc.sib.global.evnt.out"
        ), cbeOut.head.flowMessage.header) should be (empty)

        val noCbeOut = filterEnvelopes(result.out)(headerMissingFilter(bs.HEADER_EVENT_VENDOR))
        noCbeOut should have size 1
        verifyHeader(Map(
          bs.HEADER_CBE_ENABLED -> false,
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

      withDispatcher(FlowEnvelope(propsInstore), FlowEnvelope(propsCentral)) { (cfg, result) =>
        result.worklist should have size 2
        result.error should be (empty)

        result.out should have size 2

        val instore = filterEnvelopes(result.out)(headerFilter("InStoreCommunication")("1"))
        val central = filterEnvelopes(result.out)(headerFilter("InStoreCommunication")("0"))

        instore should have size 1
        verifyHeader(Map(
          "Description" -> "SalesDataFromScale",
          "DestinationName" -> "TestFile",
          bs.HEADER_EVENT_VENDOR -> "sonic75",
          bs.HEADER_EVENT_PROVIDER -> "central",
          bs.HEADER_EVENT_DEST -> "queue:cc.sib.global.data.out"
        ), instore.head.flowMessage.header)

        central should have size 1
        verifyHeader(Map(
          "Description" -> "SalesDataFromScale",
          "DestinationName" -> "TestFile",
          "Filename" -> "TestFile",
          "DestinationPath" -> "C:/Scale/Inbound/",
          bs.HEADER_EVENT_VENDOR -> "activemq",
          bs.HEADER_EVENT_PROVIDER -> "activemq",
          bs.HEADER_EVENT_DEST -> "XPDinteg_PosClientToQ"
        ), central.head.flowMessage.header)

      }
    }
  }
}
