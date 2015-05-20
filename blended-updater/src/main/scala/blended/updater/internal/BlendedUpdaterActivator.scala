package blended.updater.internal

import java.io.File
import scala.reflect.runtime.universe
import org.helgoboss.domino.DominoActivator
import akka.actor.ActorSystem
import blended.updater.Updater
import blended.container.context.ContainerIdentifierService
import blended.akka.ActorSystemAware

class BlendedUpdaterActivator extends ActorSystemAware {
  whenBundleActive {
    setupBundleActor { config =>
      log.info(s"About to start ${getClass()}")
      val configDir = config.idSvc.getContainerContext().getContainerConfigDirectory()
      val installDir = new File(config.idSvc.getContainerContext().getContainerDirectory(), "installations").getAbsoluteFile()
      val restartFramework = { () =>
        val frameworkBundle = bundleContext.getBundle(0)
        frameworkBundle.update()
      }
      val runtimeConfigRepository = ???
      val launcherConfigRepository = ???
      Updater.props(configDir, installDir, runtimeConfigRepository, launcherConfigRepository, restartFramework)
    }
  }
}