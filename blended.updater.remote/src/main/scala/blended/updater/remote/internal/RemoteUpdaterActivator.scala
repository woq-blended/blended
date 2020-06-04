package blended.updater.remote.internal

import java.io.File

import blended.akka.ActorSystemWatching
import blended.domino.TypesafeConfigWatching
import blended.persistence.PersistenceService
import blended.updater.remote.{FileSystemOverlayConfigPersistor, FileSystemRuntimeConfigPersistor, PersistentContainerStatePersistor, RemoteUpdater}
import blended.util.logging.Logger
import com.typesafe.config.ConfigException
import domino.DominoActivator
import org.osgi.framework.ServiceRegistration

import scala.util.Try

class RemoteUpdaterActivator
  extends DominoActivator
  with ActorSystemWatching
  with TypesafeConfigWatching {

  private[this] val log = Logger[RemoteUpdaterActivator]

  whenBundleActive {

    whenTypesafeConfigAvailable { (config, idService) =>
      log.debug(s"About to activate ${getClass()}")

      try {
        val rcDir = new File(config.getString("repository.runtimeConfigsPath"))
        val ocDir = new File(config.getString("repository.overlayConfigsPath"))

        val runtimeConfigPersistor = new FileSystemRuntimeConfigPersistor(rcDir)
        val overlayConfigPersistor = new FileSystemOverlayConfigPersistor(ocDir)

        whenServicePresent[PersistenceService] { persistenceService =>
          log.debug(s"PersistenceService available. About to instantiate RemoteUpdater: ${persistenceService}")

          onStop {
            log.debug(s"PersistenceService no longer available: ${persistenceService}")
          }

          val containerStatePersistor = new PersistentContainerStatePersistor(persistenceService)
          log.debug(s"Created persistent container state persistor: ${containerStatePersistor}")
          if (log.isDebugEnabled) {
            log.debug(s"Already persisted ContainerStates: ${Try(containerStatePersistor.findAllContainerStates())}")
          }

          val remoteUpdater = new RemoteUpdater(runtimeConfigPersistor, containerStatePersistor, overlayConfigPersistor)
          log.debug(s"About to register RemoteUpdater in OSGi service registry: ${remoteUpdater}")
          remoteUpdater.providesService[RemoteUpdater]

        }
      } catch {
        case e : ConfigException =>
          val msg = "Invalid or missing bundle configuration. Cannot initialize RemoteUpdater."
          log.error(e)(msg)
      }

      def registerCommands(srv : AnyRef, cmds : Seq[(String, String)]) : ServiceRegistration[Object] = {
        val (commands, descriptions) = cmds.unzip
        log.debug(s"About to register OSGi console commands: ${commands}")
        srv.providesService[Object](
          "osgi.command.scope" -> "blended.updater.remote",
          "osgi.command.function" -> commands.toArray,
          "blended.osgi.command.description" -> descriptions.toArray
        )
      }

      whenServicePresent[RemoteUpdater] { remoteUpdater =>
        log.debug("About to register OSGi console commands for remote updater")
        val commands = new RemoteCommands(remoteUpdater)
        registerCommands(commands, commands.commands)
      }
    }
  }
}

object RemoteUpdaterActivator {

  final case class RemoteUpdaterConfig()

}
