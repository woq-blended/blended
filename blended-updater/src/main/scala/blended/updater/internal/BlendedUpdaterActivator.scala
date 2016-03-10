package blended.updater.internal

import java.io.File
import scala.reflect.runtime.universe
import scala.util.Failure
import scala.util.Success
import org.osgi.framework.ServiceRegistration
import com.typesafe.config.ConfigFactory
import blended.akka.ActorSystemWatching
import blended.updater.Updater
import blended.updater.UpdaterConfig
import blended.updater.config.ConfigWriter
import blended.updater.config.ProfileLookup
import blended.updater.config.RuntimeConfig
import domino.DominoActivator
import blended.updater.config.OverlayRef
import blended.updater.ProfileActivator

case class UpdateEnv(
  launchedProfileName: String,
  launchedProfileVersion: String,
  launchProfileLookupFile: Option[File],
  profilesBaseDir: File,
  launchedProfileDir: Option[File],
  overlays: Option[Set[OverlayRef]])

class BlendedUpdaterActivator extends DominoActivator with ActorSystemWatching {

  private[this] var commandsReg: Option[ServiceRegistration[_]] = None

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      log.info(s"About to setup ${getClass()}")

      val restartFrameworkAction = { () =>
        val frameworkBundle = bundleContext.getBundle(0)
        frameworkBundle.update()
      }

      readUpdateEnv() match {
        case None =>
          sys.error("Cannot detect updateable environment. Did you used the blended launcher?")

        case Some(updateEnv) =>
          println("Blended Updated env: " + updateEnv)

          val profileActivator = new ProfileActivator {
            override def apply(
              newName: String,
              newVersion: String,
              newOverlays: Set[OverlayRef]): Boolean = {
              // TODO: Error reporting
              updateEnv match {
                case UpdateEnv(_, _, Some(lookupFile), _, _, _) =>
                  // TODO: write Config
                  val config = ConfigFactory.parseFile(lookupFile).resolve()
                  ProfileLookup.read(config) match {
                    case Success(profileLookup) =>
                      val newConfig = profileLookup.copy(
                        profileName = newName,
                        profileVersion = newVersion,
                        overlays = newOverlays.toSeq
                      )
                      log.debug(s"About to update profile lookup file: ${lookupFile} with config: ${newConfig}")
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
          }

          val actor = setupBundleActor(cfg,
            Updater.props(
              baseDir = updateEnv.profilesBaseDir,
              profileActivator = profileActivator,
              restartFramework = restartFrameworkAction,
              config = UpdaterConfig.fromConfig(cfg.config),
              launchedProfileDir = updateEnv.launchedProfileDir.orNull
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

          //          val osgiCommands = new OsgiCommands(bundleContext)
          //          registerCommands(osgiCommands, osgiCommands.commands)

          val commands = new Commands(actor, Some(updateEnv))(cfg.system)
          registerCommands(commands, commands.commandsWithDescription)

      }
    }
  }

  def readUpdateEnv(): Option[UpdateEnv] = try {
    val props = blended.launcher.runtime.Branding.getProperties()
    println("Blended Launcher detected: " + props)
    val pName = Option(props.getProperty(RuntimeConfig.Properties.PROFILE_NAME))
    val pVersion = Option(props.getProperty(RuntimeConfig.Properties.PROFILE_VERSION))
    val pProfileLookupFile = Option(props.getProperty(RuntimeConfig.Properties.PROFILE_LOOKUP_FILE))
    val pProfilesBaseDir = Option(props.getProperty(RuntimeConfig.Properties.PROFILES_BASE_DIR))
    val pProfileDir = Option(props.getProperty(RuntimeConfig.Properties.PROFILE_DIR))
    val overlays = Option(props.getProperty(RuntimeConfig.Properties.OVERLAYS))
    val overlayRefs = overlays.map { o =>
      o.split("[,]").toList.map(_.split("[:]", 2)).flatMap {
        case Array(n, v) => Some(OverlayRef(n, v))
        case x =>
          log.debug("Unsupported overlay: " + x.mkString(":"))
          None
      }.toSet
    }
    Some(
      UpdateEnv(
        launchedProfileName = pName.get,
        launchedProfileVersion = pVersion.get,
        launchProfileLookupFile = pProfileLookupFile.map(f => new File(f)),
        profilesBaseDir = new File(pProfilesBaseDir.get),
        launchedProfileDir = pProfileDir.map(f => new File(f)),
        overlays = overlayRefs
      )
    )
  } catch {
    case e: NoClassDefFoundError =>
      // could not load optional branding class
      None
    case e: NoSuchElementException =>
      // could not found some required properties
      None
  }

}

