package blended.updater.internal

import java.io.File
import scala.reflect.runtime.universe
import org.helgoboss.domino.DominoActivator
import akka.actor.ActorSystem
import blended.updater.Updater
import blended.container.context.ContainerIdentifierService
import blended.akka.ActorSystemAware
import blended.updater.InMemoryRuntimeConfigRepository
import blended.updater.DummyLauncherConfigRepository

class BlendedUpdaterActivator extends ActorSystemAware {
  whenBundleActive {
    setupBundleActor { config =>
      log.info(s"About to setup ${getClass()}")
      val configDir = config.idSvc.getContainerContext().getContainerConfigDirectory()
      val installDir = new File(config.idSvc.getContainerContext().getContainerDirectory(), "installations").getAbsoluteFile()
      val restartFrameworkAction = { () =>
        val frameworkBundle = bundleContext.getBundle(0)
        frameworkBundle.update()
      }

      val configFile = new File(configDir, "blended.updater.conf")
      val runtimeConfigRepository = new FileBasedRuntimeConfigRepository(configFile, "blended.updater.runtimeConfigs")

      // FIXME: use non-dummy impl
      val launcherConfigRepository = new DummyLauncherConfigRepository()

      Updater.props(configDir, installDir, runtimeConfigRepository, launcherConfigRepository, restartFrameworkAction)
    }
  }
}