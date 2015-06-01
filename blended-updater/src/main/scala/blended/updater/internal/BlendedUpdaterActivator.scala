package blended.updater.internal

import java.io.File
import akka.actor.ActorRef
import blended.akka.ActorSystemAware
import blended.akka.OSGIActorConfig
import blended.launcher.DummyLauncherConfigRepository
import blended.updater.Updater
import akka.actor.Actor
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorSystem
import java.util.concurrent.TimeUnit.SECONDS
import akka.pattern._
import akka.util.Timeout
import org.osgi.framework.ServiceRegistration

class BlendedUpdaterActivator extends ActorSystemAware {

  private[this] var commandsReg: Option[ServiceRegistration[_]] = None

  whenBundleActive {

    val mainActorFactory: PropsFactory = { config =>
      log.info(s"About to setup ${getClass()}")
      val configDir = config.idSvc.getContainerContext().getContainerConfigDirectory()
      val installDir = new File(config.idSvc.getContainerContext().getContainerDirectory(), "installations").getAbsoluteFile()
      val restartFrameworkAction = { () =>
        val frameworkBundle = bundleContext.getBundle(0)
        frameworkBundle.update()
      }

      val configFile = new File(configDir, "blended.updater.conf")
      val unstagedConfigRepository = new FileBasedRuntimeConfigRepository(configFile, "blended.updater.unstagedRuntimeConfigs")
      val stagedConfigRepository = new FileBasedRuntimeConfigRepository(configFile, "blended.updater.runtimeConfigs")

      // FIXME: use non-dummy impl
      val launcherConfigRepository = new DummyLauncherConfigRepository()

      Updater.props(configDir, installDir, unstagedConfigRepository, stagedConfigRepository, launcherConfigRepository, restartFrameworkAction)
    }

    setupBundleActor(mainActorFactory)

  }

  override def postStartBundleActor(config: OSGIActorConfig, updater: ActorRef): Unit = {
    commandsReg = Option(new Commands(updater)(config.system).providesService[Object](
      "osgi.command.scope" -> "blended.updater",
      "osgi.command.function" -> "runtimeConfigs"
    ))
  }

  override def preStopBundleActor(config: OSGIActorConfig, updater: ActorRef): Unit = {
    commandsReg.map { reg =>
      reg.unregister()
      commandsReg = None
    }
  }
}

class Commands(updater: ActorRef)(implicit val actorSystem: ActorSystem) {

  def runtimeConfigs(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val configs = Await.result(
      ask(updater, Updater.GetRuntimeConfigs(UUID.randomUUID().toString())).mapTo[Updater.StagedUpdates],
      timeout.duration)
    configs
  }

}