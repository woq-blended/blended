package blended.akka.internal

import akka.actor.ActorSystem
import akka.event.LogSource
import akka.osgi.ActorSystemActivator
import blended.container.context.{ContainerContext, ContainerIdentifierService}
import com.typesafe.config.Config
import domino.DominoActivator
import domino.capsule.Capsule
import org.osgi.framework.BundleContext

object BlendedAkkaActivator {
  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }
}

class BlendedAkkaActivator extends DominoActivator {

  private class AkkaCapsule(bundleContext: BundleContext, containerContext: ContainerContext)
      extends ActorSystemActivator with Capsule {

    override def start(): Unit = start(bundleContext)

    override def stop(): Unit = stop(bundleContext)

    def configure(osgiContext: BundleContext, system: ActorSystem): Unit = {
      val log = system.log

      log info "Registering Actor System as Service."
      registerService(osgiContext, system)

      log info s"ActorSystem [${system.name}] initialized."
    }

    override def getActorSystemName(context: BundleContext): String = "BlendedActorSystem"

    override def getActorSystemConfiguration(context: BundleContext): Config = containerContext.getContainerConfig()
  }

  whenBundleActive {
    whenServicePresent[ContainerIdentifierService] { svc =>
      addCapsule(new AkkaCapsule(bundleContext, svc.getContainerContext()))
    }
  }
}
  
