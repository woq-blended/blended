package blended.updater.internal

import java.io.File

import scala.reflect.runtime.universe

import org.helgoboss.domino.DominoActivator

import akka.actor.ActorSystem
import blended.updater.UpdaterActor
import de.wayofquality.blended.container.context.ContainerIdentifierService

class BlendedUpdaterActivator extends DominoActivator {

  whenBundleActive {

    val servicePid = bundleContext.getBundle().getSymbolicName()

    whenServicesPresent[ActorSystem, ContainerIdentifierService] { (actorSystem, idService) =>
      log.info(s"About to start ${getClass()}")
      val configDir = idService.getContainerContext().getContainerConfigDirectory()
      val installDir = new File(idService.getContainerContext().getContainerDirectory(), "installations").getAbsoluteFile()
      val updaterActor = actorSystem.actorOf(UpdaterActor.props(bundleContext, configDir, installDir))
    }

  }

}