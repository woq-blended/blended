package blended.akka.internal

import akka.actor.ActorSystem
import blended.container.context.api.ContainerContext
import blended.util.logging.Logger
import domino.DominoActivator

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

class BlendedAkkaActivator extends DominoActivator {

  private[this] val log = Logger[BlendedAkkaActivator]

  whenBundleActive {
    whenServicePresent[ContainerContext] { ctCtxt =>
      val ctConfig = ctCtxt.containerConfig

      log.trace(s"$ctConfig")

      try {
        val system : ActorSystem = ActorSystem.create("BlendedActorSystem", ctConfig, classOf[ActorSystem].getClassLoader())
        system.providesService[ActorSystem]

        onStop {
          // TODO: Should we really wait here ?
          log.info("Stopping ActorSystem")
          Await.result(system.terminate(), 10.seconds)
        }
      } catch {
        case NonFatal(e) =>
          log.error(s"Error starting actor system [$e]")
          throw e
      }
    }
  }
}

