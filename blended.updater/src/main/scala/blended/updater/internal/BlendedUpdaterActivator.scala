package blended.updater.internal

import java.io.File

import blended.akka.ActorSystemWatching
import blended.updater.config.{ConfigWriter, ProfileLookup, Profile}
import blended.updater.{ProfileActivator, ProfileId, Updater, UpdaterConfig}
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory
import domino.DominoActivator
import org.osgi.framework.ServiceRegistration

import scala.util.{Failure, Success}

case class UpdateEnv(
    launchedProfileName: String,
    launchedProfileVersion: String,
    launchProfileLookupFile: Option[File],
    profilesBaseDir: File,
    launchedProfileDir: Option[File]
)

class BlendedUpdaterActivator extends DominoActivator with ActorSystemWatching {

  private[this] val log = Logger[BlendedUpdaterActivator]

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      log.info(s"About to setup ${getClass()}")

      val restartFrameworkAction = { () =>
        val frameworkBundle = bundleContext.getBundle(0)
        frameworkBundle.update()
      }

      readUpdateEnv() match {
        case None =>
          sys.error(
            "Cannot detect updateable environment. You need to use the blended launcher to enable the update feature.")

        case Some(updateEnv) =>
          log.info("Blended Updated env: " + updateEnv)

          val actor = setupBundleActor(
            cfg,
            Updater.props(
              baseDir = updateEnv.profilesBaseDir,
              profileActivator = profileActivator(updateEnv),
              restartFramework = restartFrameworkAction,
              config = UpdaterConfig.fromConfig(cfg.config),
              launchedProfileDir = updateEnv.launchedProfileDir.orNull,
              launchedProfileId = ProfileId(updateEnv.launchedProfileName, updateEnv.launchedProfileVersion)
            )
          )

          def registerCommands(srv: AnyRef, cmds: Seq[(String, String)]): ServiceRegistration[Object] = {
            val (commands, descriptions) = cmds.unzip
            srv.providesService[Object](
              "osgi.command.scope" -> "blended.updater",
              "osgi.command.function" -> commands.toArray,
              "blended.osgi.command.description" -> descriptions.toArray
            )

          }

          val commands = new Commands(actor, Some(updateEnv))(cfg.system)
          registerCommands(commands, commands.commandsWithDescription)

      }
    }
  }

  private def readUpdateEnv(): Option[UpdateEnv] =
    try {
      val props = blended.launcher.runtime.Branding.getProperties()
      log.info("Blended Launcher detected: " + props)
      val pName = Option(props.getProperty(Profile.Properties.PROFILE_NAME))
      val pVersion = Option(props.getProperty(Profile.Properties.PROFILE_VERSION))
      val pProfileLookupFile = Option(props.getProperty(Profile.Properties.PROFILE_LOOKUP_FILE))
      val pProfilesBaseDir = Option(props.getProperty(Profile.Properties.PROFILES_BASE_DIR))
      val pProfileDir = Option(props.getProperty(Profile.Properties.PROFILE_DIR))
      Some(
        UpdateEnv(
          launchedProfileName = pName.get,
          launchedProfileVersion = pVersion.get,
          launchProfileLookupFile = pProfileLookupFile.map(f => new File(f)),
          profilesBaseDir = new File(pProfilesBaseDir.get),
          launchedProfileDir = pProfileDir.map(f => new File(f))
        )
      )
    } catch {
      case _: NoClassDefFoundError =>
        // could not load optional branding class
        None
      case _: NoSuchElementException =>
        // could not found some required properties
        None
    }

  private def profileActivator(updateEnv: UpdateEnv) = new ProfileActivator {
    override def apply(
        newName: String,
        newVersion: String
    ): Boolean = {
      // TODO: Error reporting
      updateEnv match {
        case UpdateEnv(_, _, Some(lookupFile), _, _) =>
          val config = ConfigFactory.parseFile(lookupFile).resolve()
          ProfileLookup.read(config) match {
            case Success(profileLookup) =>
              val newConfig = profileLookup.copy(
                profileName = newName,
                profileVersion = newVersion
              )
              log.debug(s"About to update profile lookup file: [$lookupFile] with config: [$newConfig]")
              ConfigWriter.write(ProfileLookup.toConfig(newConfig), lookupFile, None)
              true
            case Failure(_) =>
              false
          }

        case _ =>
          // no lookup file
          false
      }
    }
  }

}
