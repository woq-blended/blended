package blended.updater.internal

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

import scala.concurrent.Await
import scala.concurrent.duration.HOURS
import scala.concurrent.duration.MINUTES
import scala.reflect.runtime.universe
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.osgi.framework.ServiceRegistration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import blended.akka.ActorSystemAware
import blended.akka.OSGIActorConfig
import blended.launcher.config.LauncherConfig
import blended.updater.Updater
import blended.updater.Updater.RuntimeConfigActivated
import blended.updater.Updater.RuntimeConfigActivationFailed
import blended.updater.Updater.RuntimeConfigAdded
import blended.updater.Updater.RuntimeConfigAdditionFailed
import blended.updater.Updater.RuntimeConfigStaged
import blended.updater.Updater.RuntimeConfigStagingFailed
import blended.updater.config.ConfigConverter
import blended.updater.config.ConfigWriter
import blended.updater.config.ProfileLookup
import blended.updater.config.RuntimeConfig

case class UpdateEnv(
  launchedProfileName: String,
  launchedProfileVersion: String,
  launchProfileLookupFile: Option[File])

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

      val profileUpdater = { (name: String, version: String) =>
        // TODO: Error reporting
        readUpdateEnv() match {
          case Some(UpdateEnv(_, _, Some(lookupFile))) =>
            // TODO: write Config
            val config = ConfigFactory.parseFile(lookupFile).resolve()
            ProfileLookup.read(config) match {
              case Success(profileLookup) =>
                val newConfig = profileLookup.copy(profileName = name, profileVersion = version)
                ConfigWriter.write(ProfileLookup.toConfig(newConfig), lookupFile, None)
                true
              case Failure(e) =>
                false
            }

          case _ =>
            // no lookup file
            false
        }
      }

      Updater.props(installDir, profileUpdater, restartFrameworkAction)
    }

    setupBundleActor(mainActorFactory)

  }

  def readUpdateEnv() = Try {
    val props = blended.launcher.runtime.Branding.getProperties()
    println("Blended Launcher detected: " + props)
    val pName = Option(props.getProperty(RuntimeConfig.Properties.PROFILE_NAME))
    val pVersion = Option(props.getProperty(RuntimeConfig.Properties.PROFILE_VERSION))
    val pProfileLookupFile = Option(props.getProperty(RuntimeConfig.Properties.PROFILE_LOOKUP_FILE))
    Some(UpdateEnv(pName.get, pVersion.get, pProfileLookupFile.map(f => new File(f))))
  } getOrElse (None)

  override def postStartBundleActor(config: OSGIActorConfig, updater: ActorRef): Unit = {
    val updateEnv = readUpdateEnv()

    println("Blended Updated env: " + updateEnv)

    val commands = new Commands(updater, updateEnv)(config.system)
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

class Commands(updater: ActorRef, env: Option[UpdateEnv])(implicit val actorSystem: ActorSystem) {

  val commands = Array("show", "add", "stage", "activate", "convertLauchConfigToRuntimeConfig")

  def show(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val configs = Await.result(
      ask(updater, Updater.GetRuntimeConfigs(UUID.randomUUID().toString())).mapTo[Updater.RuntimeConfigs],
      timeout.duration)

    def format(config: RuntimeConfig): String = {
      val activeSuffix = env match {
        case Some(e) if e.launchedProfileName == config.name && e.launchedProfileVersion == config.version => " [active]"
        case _ => ""
      }
      s"${config.name}-${config.version}${activeSuffix}"
    }

    "staged: " + configs.staged.map(format).mkString(", ") + "\n" +
      "pending: " + configs.pending.map(format).mkString(", ") + "\n" +
      "invalid: " + configs.invalid.map(format).mkString(", ")
  }

  def add(file: File): AnyRef = {
    val config = ConfigFactory.parseFile(file).resolve()
    val runtimeConfig = RuntimeConfig.read(config).get
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
    env match {
      case Some(UpdateEnv(_, _, Some(lookupFile))) =>
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
      case _ =>
        sys.error("No updateable environment detected. No profile lookup file defined.")
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