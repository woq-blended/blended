package blended.streams.dispatcher.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.bridge.internal.BridgeActivator
import blended.streams.dispatcher.{IllegalResourceType, MissingOutboundRouting, MissingResourceType}
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.MsgProperty.Implicits._
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.{LoggingFreeSpec, LoggingFreeSpecLike}
import blended.util.logging.Logger
import org.scalatest.Matchers
import blended.streams.dispatcher.DispatcherBuilder._

import scala.concurrent.duration._

class DispatcherGraphBuilderSpec extends LoggingFreeSpec
  with LoggingFreeSpecLike
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] val log = Logger[DispatcherGraphBuilderSpec]
  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  def runDispatcher(testMessages: FlowEnvelope*): DispatcherResult = {

    System.setProperty("SIBCountry", "cc")
    System.setProperty("SIBLocation", "09999")

    withSimpleBlendedContainer(baseDir) { sr =>
      withStartedBundles(sr)(Seq(
        "blended.akka" -> Some(() => new BlendedAkkaActivator()),
        "blended.jms.bridge" -> Some(() => new BridgeActivator())
      )) { sr =>

        implicit val timeout = 3.seconds

        try {
          val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](sr)(None)
          implicit val system : ActorSystem = mandatoryService[ActorSystem](sr)(None)
          val provider : BridgeProviderRegistry = mandatoryService[BridgeProviderRegistry](sr)(None)

          implicit val eCtxt = system.dispatcher
          implicit val materializer = ActorMaterializer()

          val cfg = ResourceTypeRouterConfig.create(
            idSvc,
            provider,
            idSvc.containerContext.getContainerConfig().getConfig("blended.streams.dispatcher")
          ).get

          DispatcherExecutor.execute(system, idSvc, cfg, testMessages:_*)

        } catch {
          case t: Throwable => fail(t)
        }
      }
    }
  }

  val headerExistsFilter : String => FlowEnvelope => Boolean = key => env => env.flowMessage.header.isDefinedAt(key)
  val headerMissingFilter : String => FlowEnvelope => Boolean = key => env => !env.flowMessage.header.isDefinedAt(key)
  def filterEnvelopes(envelopes : Seq[FlowEnvelope])(f : FlowEnvelope => Boolean) : Seq[FlowEnvelope] = envelopes.filter(f)

  def verifyHeader(expected: FlowMessageProps, env: FlowEnvelope) : List[(String, MsgProperty[_], Option[MsgProperty[_]])] = {

    val broken = expected.filter { p =>
      env.flowMessage.header.get(p._1) match {
        case None => true
        case Some(ep) => !(p._2 === ep)
      }
    }

    broken.map{ case (k, p) =>
      (k, p, env.flowMessage.header.get(k))
    }.toList
  }

  "The Dispatcher RouteBuilder should" - {

    "add all configured default Headers" in {

      val props : FlowMessageProps = Map("ResourceType" -> "SagTest")
      val good = FlowEnvelope(FlowMessage("Normal", props))

      val result = runDispatcher(good)

      result.out should have size(1)
      result.out.head.flowMessage.header[String]("ComponentName") should be (Some("Dispatcher"))
      result.out.head.flowMessage.header[String]("ResourceType") should be (Some("SagTest"))
    }

    "yield a MissingResourceType exception when the resourcetype is not set in the inbound message" in {

      val msg = FlowEnvelope(FlowMessage("Normal", FlowMessage.noProps))

      val result = runDispatcher(msg)

      result.out should be (empty)
      result.event should be (empty)
      result.error should have size(1)

      result.error.head.exception should be (defined)

      assert(result.error.head.exception.forall(t => t.isInstanceOf[MissingResourceType]))
    }

    "yield an IllegalResourceType exception when the resourcetype given in the message is not configured" in {

      val props : FlowMessageProps = Map("ResourceType" -> "Dummy")
      val msg = FlowEnvelope(FlowMessage("Normal", props))

      val result = runDispatcher(msg)

      result.out should be (empty)
      result.event should be (empty)
      result.error should have size(1)

      result.error.head.exception should be (defined)

      assert(result.error.head.exception.forall(t => t.isInstanceOf[IllegalResourceType]))
    }

    "yield an MissingOutboundConfig  exception when the resourcetype has no outbound blocks configured" in {

      val props : FlowMessageProps = Map("ResourceType" -> "NoOutbound")
      val msg = FlowEnvelope(FlowMessage("Normal", props))

      val result = runDispatcher(msg)

      result.out should be (empty)
      result.event should be (empty)
      result.error should have size(1)

      result.error.head.exception should be (defined)

      assert(result.error.head.exception.forall(t => t.isInstanceOf[MissingOutboundRouting]))
    }

    "fanout for all out outbounds" in {
      val props : FlowMessageProps = Map("ResourceType" -> "KPosData")

      val result = runDispatcher(FlowEnvelope(props))

      result.out should have size (2)

      result.out.foreach{ env =>
        verifyHeader(Map(
          "ResourceType" -> "KPosData"
        ), env) should be (empty)
      }
    }

    "correctly populate the Cbe headers if CBE is enabled on the resourcetype" in {

      val noCbe: FlowMessageProps = Map("ResourceType" -> "ShopRegister")
      val withCbe : FlowMessageProps = Map("ResourceType" -> "Msg2TopicScaleAssortment")

      val result = runDispatcher(FlowEnvelope(noCbe), FlowEnvelope(withCbe))

      result.out should have size (2)

      val cbeOut = filterEnvelopes(result.out)(headerExistsFilter(HEADER_EVENT_VENDOR))
      cbeOut should have size(1)
      verifyHeader(Map(
        HEADER_CBE_ENABLED -> true,
        HEADER_EVENT_VENDOR -> "sonic75",
        HEADER_EVENT_PROVIDER -> "central",
        HEADER_EVENT_DEST -> "queue:cc.sib.global.evnt.out"
      ), cbeOut.head) should be (empty)

      val noCbeOut = filterEnvelopes(result.out)(headerMissingFilter(HEADER_EVENT_VENDOR))
      noCbeOut should have size (1)
      verifyHeader(Map(
        HEADER_CBE_ENABLED -> false,
      ), noCbeOut.head) should be (empty)

      result.event should be (empty)
      result.error should be (empty)

    }

  }
}
