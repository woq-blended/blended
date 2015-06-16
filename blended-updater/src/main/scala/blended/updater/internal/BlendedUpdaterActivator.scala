package blended.updater.internal

import java.io.File
import akka.actor.ActorRef
import blended.akka.ActorSystemAware
import blended.akka.OSGIActorConfig
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
import com.typesafe.config.ConfigFactory
import blended.updater.Updater._
import com.typesafe.config.ConfigRenderOptions
import java.io.FileOutputStream
import java.io.PrintStream
import blended.updater.config.LauncherConfig
import blended.updater.config.RuntimeConfig
import blended.updater.config.ConfigConverter

class BlendedUpdaterActivator extends ActorSystemAware {

  private[this] var commandsReg: Option[ServiceRegistration[_]] = None

  whenBundleActive {

    val mainActorFactory: PropsFactory = { config =>
      log.info(s"About to setup ${getClass()}")
      val configDir = config.idSvc.getContainerContext().getContainerConfigDirectory()
      val installDir = new File(config.idSvc.getContainerContext().getContainerDirectory(), "profiles").getAbsoluteFile()
      val restartFrameworkAction = { () =>
        val frameworkBundle = bundleContext.getBundle(0)
        frameworkBundle.update()
      }

      //      val configFile = new File(configDir, "blended.updater.conf")
      //      val launcherFile = new File(configDir, "blended.launcher.conf")

      val launcherConfigSetter = { config: LauncherConfig =>
        // TODO: write Config
      }

      Updater.props(installDir, launcherConfigSetter, restartFrameworkAction)
    }

    setupBundleActor(mainActorFactory)

  }

  override def postStartBundleActor(config: OSGIActorConfig, updater: ActorRef): Unit = {
    val commands = new Commands(updater)(config.system)
    commandsReg = Option(commands.providesService[Object](
      "osgi.command.scope" -> "blended.updater",
      "osgi.command.function" -> commands.commands
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

  val commands = Array("show", "add", "stage", "activate", "convertLauchConfigToRuntimeConfig")

  def show(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val configs = Await.result(
      ask(updater, Updater.GetRuntimeConfigs(UUID.randomUUID().toString())).mapTo[Updater.RuntimeConfigs],
      timeout.duration)
      
    def format(config: RuntimeConfig): String = s"${config.name}-${config.version}"
    
    "staged: " + configs.staged.map(format).mkString(", ") + "\n" +
      "pending: " + configs.pending.map(format).mkString(", ") + "\n" +
      "invalid: " + configs.invalid.map(format).mkString(", ")
  }

  def add(file: File): AnyRef = {
    val config = ConfigFactory.parseFile(file).resolve()
    val runtimeConfig = RuntimeConfig.read(config)
    println("About to add: " + runtimeConfig)

    implicit val timeout = Timeout(5, SECONDS)
    val reqId = UUID.randomUUID().toString()
    Await.result(
      ask(updater, Updater.AddRuntimeConfig(reqId, runtimeConfig)), timeout.duration) match {
        case RuntimeConfigAdded(`reqId`) =>
          "Added: " + runtimeConfig
        case RuntimeConfigAdditionFailed(`reqId`, error) =>
          "Failed: " + error
        case x =>
          "Error: " + x
      }
  }

  def stage(name: String, version: String): AnyRef = {
    implicit val timeout = Timeout(1, HOURS)
    val reqId = UUID.randomUUID().toString()
    Await.result(
      ask(updater, Updater.StageRuntimeConfig(reqId, name, version)), timeout.duration) match {
        case RuntimeConfigStaged(`reqId`) =>
          "Staged: " + name + " " + version
        case RuntimeConfigStagingFailed(`reqId`, reason) =>
          "Failed: " + reason
        case x =>
          "Error: " + x
      }
  }

  def activate(name: String, version: String): AnyRef = {
    implicit val timeout = Timeout(5, MINUTES)
    val reqId = UUID.randomUUID().toString()
    Await.result(
      ask(updater, Updater.ActivateRuntimeConfig(reqId, name, version)), timeout.duration) match {
        case RuntimeConfigActivated(`reqId`) =>
          "Activated: " + name + " " + version
        case RuntimeConfigActivationFailed(`reqId`, reason) =>
          "Failed: " + reason
        case x =>
          "Error: " + x
      }
  }

  def convertLauchConfigToRuntimeConfig(launcherConfig: File, runtimeConfig: File): Unit = {
    val lc = LauncherConfig.read(launcherConfig)
    val rc = ConfigConverter.launcherConfigToRuntimeConfig(lc, "???")
    val rendered = RuntimeConfig.toConfig(rc).root().render(ConfigRenderOptions.concise().setFormatted(true).setJson(false))
    val os = new PrintStream(new FileOutputStream(runtimeConfig))
    os.print(rendered)
    os.close()
  }

}