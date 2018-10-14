package blended.streams.dispatcher.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.bridge.internal.BridgeActivator
import blended.streams.message.FlowMessage.FlowMessageProps
import blended.streams.message.MsgProperty.Implicits._
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.{LoggingFreeSpec, LoggingFreeSpecLike}
import blended.util.logging.Logger
import org.scalatest.Matchers

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

  "The Dispatcher RouteBuilder should" - {

    "add all configured default Headers" in {

      val props : FlowMessageProps = Map("ResourceType" -> "SagTest")
      val good = FlowEnvelope(FlowMessage("Normal", props))

      val result = runDispatcher(good, good, good)

      result.out should have size(3)
      result.out.head.flowMessage.header[String]("ComponentName") should be (Some("Dispatcher"))
      result.out.head.flowMessage.header[String]("ResourceType") should be (Some("SagTest"))
    }
  }
}
