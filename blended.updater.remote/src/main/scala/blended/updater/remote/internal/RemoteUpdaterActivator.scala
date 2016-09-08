package blended.updater.remote.internal

import java.io.File

import scala.reflect.runtime.universe

import org.osgi.framework.ServiceRegistration
import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigException

import blended.akka.ActorSystemWatching
import blended.updater.remote.FileSystemOverlayConfigPersistor
import blended.updater.remote.FileSystemRuntimeConfigPersistor
import blended.updater.remote.RemoteUpdater
import blended.updater.remote.TransientContainerStatePersistor
import domino.DominoActivator
import blended.persistence.PersistenceService
import blended.domino.TypesafeConfigWatching
import blended.updater.remote.PersistentContainerStatePersistor

class RemoteUpdaterActivator
    extends DominoActivator
    with ActorSystemWatching
    with TypesafeConfigWatching {

  private[this] val log = LoggerFactory.getLogger(classOf[RemoteUpdaterActivator])

  whenBundleActive {

    whenTypesafeConfigAvailable { (config, idService) =>

      try {
        val rcDir = new File(config.getString("repository.runtimeConfigsPath"))
        val ocDir = new File(config.getString("repository.overlayConfigsPath"))

        val runtimeConfigPersistor = new FileSystemRuntimeConfigPersistor(rcDir)
        val overlayConfigPersistor = new FileSystemOverlayConfigPersistor(ocDir)

        whenServicePresent[PersistenceService] { persistenceService =>

          val containerStatePersistor = new PersistentContainerStatePersistor(persistenceService)

          val remoteUpdater = new RemoteUpdater(runtimeConfigPersistor, containerStatePersistor, overlayConfigPersistor)
          log.debug("About to register RemoteUpdater")
          remoteUpdater.providesService[RemoteUpdater]

        }
      } catch {
        case e: ConfigException =>
          val msg = "Invalid or missing bundle configuration. Cannot initialize RemoteUpdater."
          log.error(msg, e)
      }

      def registerCommands(srv: AnyRef, cmds: Seq[(String, String)]): ServiceRegistration[Object] = {
        val (commands, descriptions) = cmds.unzip
        srv.providesService[Object](
          "osgi.command.scope" -> "blended.updater.remote",
          "osgi.command.function" -> commands.toArray,
          "blended.osgi.command.description" -> descriptions.toArray)
      }

      whenServicePresent[RemoteUpdater] { remoteUpdater =>
        log.debug("About to register osgi console commands for remote updater")
        val commands = new RemoteCommands(remoteUpdater)
        registerCommands(commands, commands.commands)
      }
    }
  }
}

object RemoteUpdaterActivator {

  final case class RemoteUpdaterConfig()

}