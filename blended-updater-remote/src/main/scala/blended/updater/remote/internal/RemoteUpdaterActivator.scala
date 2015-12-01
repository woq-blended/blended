package blended.updater.remote.internal

import domino.DominoActivator
import blended.updater.remote.TransientPersistor
import blended.updater.remote.RemoteUpdater
import org.osgi.framework.ServiceRegistration

class RemoteUpdaterActivator extends DominoActivator {
  whenBundleActive {
    // TODO: only register in configured so
    val remoteUpdater = new RemoteUpdater with TransientPersistor
    remoteUpdater.providesService[RemoteUpdater]

    def registerCommands(srv: AnyRef, cmds: Seq[(String, String)]): ServiceRegistration[Object] = {
      val (commands, descriptions) = cmds.unzip
      srv.providesService[Object](
        "osgi.command.scope" -> "blended.updater",
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