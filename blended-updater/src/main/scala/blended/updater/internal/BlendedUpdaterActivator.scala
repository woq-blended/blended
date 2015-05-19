package blended.updater.internal

import scala.reflect.runtime.universe
import org.helgoboss.domino.DominoActivator
import akka.actor.ActorSystem
import de.wayofquality.blended.container.context.ContainerIdentifierService
import blended.updater.UpdaterActor
import de.wayofquality.blended.akka.ConfigLocator
import de.wayofquality.blended.akka.ConfigDirectoryProvider
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

class BlendedUpdaterActivator extends DominoActivator {

  whenBundleActive {

    val log = Logger[BlendedUpdaterActivator]

    val servicePid = bundleContext.getBundle().getSymbolicName()

    whenServicesPresent[ActorSystem, ContainerIdentifierService] { (actorSystem, idService) =>
      log.info(s"About to start ${getClass()}")
      val configDir = idService.getContainerContext().getContainerConfigDirectory()
      //      val updaterActor = actorSystem.actorOf(UpdaterActor.props(bundleContext, configDir))
    }

  }

}