package blended.updater.remote.internal

import java.io.File

import blended.akka.ActorSystemWatching
import blended.updater.remote.FileSystemOverlayConfigPersistor
import blended.updater.remote.FileSystemRuntimeConfigPersistor
import blended.updater.remote.RemoteUpdater
import blended.updater.remote.TransientContainerStatePersistor
import com.typesafe.config.ConfigException
import domino.DominoActivator
import org.osgi.framework.ServiceRegistration
import org.slf4j.LoggerFactory

class RemoteUpdaterActivator
  extends DominoActivator
    with ActorSystemWatching {

  private[this] val log = LoggerFactory.getLogger(classOf[RemoteUpdaterActivator])

  whenBundleActive {

    whenActorSystemAvailable { cfg =>

      // TODO: only register if configured so

      try {

        val rcDir = new File(cfg.config.getString("repository.runtimeConfigsPath"))
        val ocDir = new File(cfg.config.getString("repository.overlayConfigsPath"))

        val runtimeConfigPersistor = new FileSystemRuntimeConfigPersistor(rcDir)
        val containerStatePersistor = new TransientContainerStatePersistor()
        val overlayConfigPersistor = new FileSystemOverlayConfigPersistor(ocDir)

        val remoteUpdater = new RemoteUpdater(runtimeConfigPersistor, containerStatePersistor, overlayConfigPersistor)
        remoteUpdater.providesService[RemoteUpdater]

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
          "blended.osgi.command.description" -> descriptions.toArray
        )
      }

      whenServicePresent[RemoteUpdater] { remoteUpdater =>
        val commands = new RemoteCommands(remoteUpdater)
        registerCommands(commands, commands.commands)
      }
    }
  }
}

object RemoteUpdaterActivator {

  final case class RemoteUpdaterConfig()

}