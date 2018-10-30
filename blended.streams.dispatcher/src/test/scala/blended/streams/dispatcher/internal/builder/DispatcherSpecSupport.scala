package blended.streams.dispatcher.internal.builder

import akka.actor.ActorSystem
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}

import scala.concurrent.duration._
import scala.reflect.ClassTag

trait DispatcherSpecSupport extends SimplePojosrBlendedContainer with PojoSrTestHelper {

  case class DispatcherExecContext(
    cfg : ResourceTypeRouterConfig,
    idSvc : ContainerIdentifierService,
    system : ActorSystem
  )

  def country : String
  def location : String
  def baseDir : String
  def loggerName : String

  def providerId(vendor: String, provider: String) : String =
    classOf[BridgeProviderConfig].getSimpleName() + s"($vendor:$provider)"

  def withDispatcherConfig[T](f : DispatcherExecContext => T)(implicit bs : DispatcherBuilderSupport) : T = {

    System.setProperty(bs.header("Country"), country)
    System.setProperty(bs.header("Location"), location)

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

        f(DispatcherExecContext(cfg = cfg, idSvc = idSvc, system = system))
      }
    }
  }


}

