package blended.akka.internal

import akka.actor.ActorSystem
import akka.event.LogSource
import akka.osgi.ActorSystemActivator
import blended.container.context.{ContainerContext, ContainerIdentifierService}
import com.typesafe.config.Config
import domino.DominoActivator
import domino.capsule.Capsule
import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object BlendedAkkaActivator {
  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }
}

class BlendedAkkaActivator extends DominoActivator {

  private[this] val log = LoggerFactory.getLogger(classOf[BlendedAkkaActivator])

  private class AkkaCapsule(bundleContext: BundleContext, containerContext: ContainerContext)
      extends ActorSystemActivator with Capsule {

    override def start(): Unit = start(bundleContext)

    override def stop(): Unit = stop(bundleContext)

    def configure(osgiContext: BundleContext, system: ActorSystem): Unit = {

      log.info("Registering Actor System as Service.")
      registerService(osgiContext, system)

      log.info(s"ActorSystem [${system.name}] initialized.")
    }

    override def getActorSystemName(context: BundleContext): String = "BlendedActorSystem"

    override def getActorSystemConfiguration(context: BundleContext): Config = try {
      containerContext.getContainerConfig()
    } catch {
      case NonFatal(e) =>
        log.warn(s"Error retrieving config for ActorSystem [${e.getMessage()}]")
        throw e
    }
  }

  whenBundleActive {
    whenServicePresent[ContainerIdentifierService] { svc =>
      addCapsule(new AkkaCapsule(bundleContext, svc.getContainerContext()))
    }
  }
}
  
