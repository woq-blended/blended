package blended.streams.dispatcher.internal.builder

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.bridge.internal.BridgeActivator
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.message.FlowEnvelope
import blended.streams.testsupport.CollectingActor
import blended.streams.testsupport.CollectingActor.Completed
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

  def collector[T](name : String)(implicit system : ActorSystem, clazz : ClassTag[T]) : (TestProbe, Sink[T, _]) = {
    val p = TestProbe(name)
    val actor = system.actorOf(CollectingActor.props[T](name, p.ref))
    (p, Sink.actorRef[T](actor, Completed))
  }

  def withDispatcherConfig[T]()(f : DispatcherExecContext => T) : T = {

    System.setProperty("SIBCountry", country)
    System.setProperty("SIBLocation", location)

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

