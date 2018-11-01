package blended.streams.dispatcher.internal.builder

import java.io.File

import akka.actor.ActorSystem
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.util.logging.Logger
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.reflect.ClassTag

trait DispatcherSpecSupport extends SimplePojosrBlendedContainer with PojoSrTestHelper {

  case class DispatcherExecContext(
    cfg : ResourceTypeRouterConfig,
    idSvc : ContainerIdentifierService,
    system : ActorSystem,
    bs : DispatcherBuilderSupport
  )

  def country: String = "cc"
  def location: String = "09999"
  def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()
  def loggerName: String = getClass().getName()

  System.setProperty("AppCountry", country)
  System.setProperty("AppLocation", location)

  def providerId(vendor: String, provider: String) : String =
    classOf[BridgeProviderConfig].getSimpleName() + s"($vendor:$provider)"

  def withDispatcherConfig[T](f : DispatcherExecContext => T) : T = {

    withSimpleBlendedContainer(baseDir) { sr =>
      withStartedBundles(sr)(Seq(
        "blended.akka" -> Some(() => new BlendedAkkaActivator()),
        "blended.jms.bridge" -> Some(() => new BridgeActivator())
      )) { sr =>

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

        val cfg = ResourceTypeRouterConfig.create(
          idSvc,
          provider,
          idSvc.containerContext.getContainerConfig().getConfig("blended.streams.dispatcher")
        ).get

        val bs = new DispatcherBuilderSupport {
          override def containerConfig: Config = idSvc.getContainerContext().getContainerConfig()
          override val streamLogger: Logger = Logger(loggerName)
        }

        f(DispatcherExecContext(cfg = cfg, idSvc = idSvc, system = system, bs = bs))
      }
    }
  }


}

