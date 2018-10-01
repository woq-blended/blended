package blended.akka.internal

import akka.actor.ActorSystem
import blended.container.context.api.ContainerIdentifierService
import blended.util.logging.Logger
import domino.DominoActivator

class BlendedAkkaActivator extends DominoActivator {

  private[this] val log = Logger[BlendedAkkaActivator]

  whenBundleActive {
    whenServicePresent[ContainerIdentifierService] { svc =>
      val ctConfig = svc.containerContext.getContainerConfig()

      log.debug(s"$ctConfig")

      val system : ActorSystem = ActorSystem.create("BlendedActorSystem", ctConfig, classOf[ActorSystem].getClassLoader())
      system.providesService[ActorSystem]

      onStop {
        system.terminate()
      }
    }
  }
}
  
