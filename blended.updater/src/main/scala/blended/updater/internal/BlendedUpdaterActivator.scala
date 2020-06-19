package blended.updater.internal

import java.io.File

import blended.akka.{ActorSystemWatching, OSGIActorConfig}
import blended.updater.config.{Profile, ProfileRef}
import blended.updater.{Updater, UpdaterConfig}
import blended.util.logging.Logger
import domino.DominoActivator

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
    whenActorSystemAvailable { cfg: OSGIActorConfig =>
      log.info(s"About to setup ${getClass()}")

      readUpdateEnv() match {
        case None =>
          sys.error(
            "Cannot detect updateable environment. You need to use the blended launcher to enable the update feature.")

        case Some(updateEnv) =>
          log.info("Blended Updated env: " + updateEnv)

          setupBundleActor(
            cfg,
            Updater.props(
              baseDir = updateEnv.profilesBaseDir,
              config = UpdaterConfig.fromConfig(cfg.config),
              launchedProfileDir = updateEnv.launchedProfileDir.orNull,
              launchedProfileRef = ProfileRef(updateEnv.launchedProfileName, updateEnv.launchedProfileVersion)
            )
          )

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

}
