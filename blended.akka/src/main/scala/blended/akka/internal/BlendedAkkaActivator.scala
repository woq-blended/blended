package blended.akka.internal

import akka.actor.ActorSystem
import blended.container.context.api.ContainerIdentifierService
import blended.util.logging.Logger
import domino.DominoActivator

import scala.util.control.NonFatal

class BlendedAkkaActivator extends DominoActivator {

  private[this] val log = Logger[BlendedAkkaActivator]

  whenBundleActive {
    whenServicePresent[ContainerIdentifierService] { svc =>
      val ctConfig = svc.containerContext.getContainerConfig()

      log.debug(s"$ctConfig")

      try {
        val system : ActorSystem = ActorSystem.create("BlendedActorSystem", ctConfig, classOf[ActorSystem].getClassLoader())
        system.providesService[ActorSystem]

        onStop {
          system.terminate()
        }
      } catch {
        case NonFatal(e) =>
          log.error(s"Error starting actor system [$e]")
          throw e
      }
    }
  }
}
  
