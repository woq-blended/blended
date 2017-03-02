package blended.updater

import java.io.File

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import blended.updater.config.OverlayConfig
import blended.updater.config.OverlayConfigCompanion
import blended.updater.config.LocalRuntimeConfig
import blended.updater.config.ResolvedRuntimeConfig
import blended.updater.config.RuntimeConfigCompanion
import com.typesafe.config.ConfigParseOptions
import blended.updater.config.LocalOverlays

class ProfileFsHelper {

  private[this] val log = LoggerFactory.getLogger(classOf[ProfileFsHelper])

  def scanForOverlayConfigs(overlayBaseDir: File): List[OverlayConfig] = {
    log.debug("Scanning for overlays configs in: {}", overlayBaseDir)

    val confFiles = Option(overlayBaseDir.listFiles).getOrElse(Array()).
      filter(f => f.isFile() && f.getName().endsWith(".conf"))

    val configs = confFiles.toList.flatMap { file =>
      Try {
        ConfigFactory.parseFile(file).resolve()
      }.
        flatMap(OverlayConfigCompanion.read) match {
          case Success(overlayConfig) =>
            List(overlayConfig)
          case Failure(e) =>
            log.error("Could not parse overlay config file: {}", Array(file, e))
            List()
        }
    }

    log.debug("Found overlay configs : {}", configs)
    configs
  }

  def scanForRuntimeConfigs(installBaseDir: File): List[LocalRuntimeConfig] = {
    log.debug("Scanning for runtime configs in {}", installBaseDir)

    val configFiles = Option(installBaseDir.listFiles).getOrElse(Array()).toList.
      flatMap { nameDir =>
        Option(nameDir.listFiles).getOrElse(Array()).toList.
          flatMap { versionDir =>
            val profileFile = new File(versionDir, "profile.conf")
            if (profileFile.exists()) Some(profileFile)
            else None
          }
      }

    log.debug("Found potential runtime config files: {}", configFiles)

    // read configs
    val runtimeConfigs = configFiles.flatMap { runtimeConfigFile =>
      Try {
        val versionDir = runtimeConfigFile.getParentFile()
        val version = versionDir.getName()
        val name = versionDir.getParentFile.getName()

        val config = ConfigFactory.parseFile(runtimeConfigFile, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
        val resolved = ResolvedRuntimeConfig(RuntimeConfigCompanion.read(config).get)
        val local = LocalRuntimeConfig(baseDir = versionDir, resolvedRuntimeConfig = resolved)

        // consistency checks
        if (local.runtimeConfig.name == name && local.runtimeConfig.version == version) {
          List(local)
        } else {
          log.warn(s"Profile name and version do not match directory names: ${runtimeConfigFile}")
          List()
        }
      }.getOrElse {
        log.warn(s"Could not read profile file: ${runtimeConfigFile}")
        List()
      }
    }
    log.debug(s"Found runtime configs: ${runtimeConfigs.zipWithIndex.map(wi => s"${wi._2}: ${wi._1}").mkString("\n")}")

    runtimeConfigs
  }

  def scanForProfiles(installBaseDir: File, runtimeConfigs: Option[List[LocalRuntimeConfig]] = None): List[LocalProfile] = {
    log.debug("Scanning for profiles in: {}", installBaseDir)

    val rcs = runtimeConfigs.getOrElse(scanForRuntimeConfigs(installBaseDir)).toList

    val runtimeConfigsWithIssues = rcs.flatMap { localConfig =>
      val issues = localConfig.validate(
        includeResourceArchives = false,
        explodedResourceArchives = true,
        checkPropertiesFile = true).toList
      log.debug(s"Runtime config ${localConfig.runtimeConfig.name}-${localConfig.runtimeConfig.version} issues: {}", issues)
      List(localConfig -> issues)

    }

    log.debug(s"Runtime configs (with issues): ${runtimeConfigsWithIssues}")

    def profileState(issues: List[String]): LocalProfile.ProfileState = issues match {
      case Seq() => LocalProfile.Staged
      case issues => LocalProfile.Pending(issues)
    }

    val fullProfiles = runtimeConfigsWithIssues.flatMap {
      case (localRuntimeConfig, issues) =>
        val profileDir = localRuntimeConfig.baseDir

        // scan for overlays
        val overlayDir = new File(profileDir, "overlays")
        val overlayFiles = Option(overlayDir.listFiles()).getOrElse(Array()).filter(f => f.getName().endsWith(".conf")).toList
        if (overlayFiles.isEmpty) {
          log.warn("Could not found any overlay configs for profile: {}", localRuntimeConfig.profileFileLocation)
          //          log.info("Migrating legacy profile. Generating base overlay config")
          //          // We create a transient base overlay
          // TODO: Remove timely
          //          List(Profile(localRuntimeConfig, LocalOverlays(Set(), profileDir), profileState(issues)))
          List()
        } else overlayFiles.flatMap { file =>
          Try {
            ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
          }.flatMap { c =>
            LocalOverlays.read(c, profileDir)
          } match {
            case Failure(e) =>
              log.error(s"Could not load overlay config file: ${file}", e)
              None
            case Success(localOverlays) =>
              val canonicalFile = LocalOverlays.preferredConfigFile(localOverlays.overlays.map(_.overlayRef), profileDir)
              if (canonicalFile != file) {
                log.error("Skipping found overlays file because filename does not match the expected file name: {}", file)
                List()
              } else {
                val overlayIssues = localOverlays.validate() match {
                  case Seq() =>
                    // no conflicts, now check if already materialized
                    if (localOverlays.isMaterialized()) {
                      List()
                    } else {
                      List("Overlays not materialized")
                    }
                  case issues =>
                    log.error("Skipping found overlays file because it is not valid: {}. Issue: {}",
                      Array(file, issues.mkString(" / ")))
                    issues.toList
                }
                log.debug("Found overlay:", localOverlays)
                log.debug("Found overlay issues: {}", issues)
                List(LocalProfile(localRuntimeConfig, localOverlays, profileState(issues ::: overlayIssues)))
              }
          }
        }
    }

    fullProfiles
  }

}

object ProfileFsHelper extends ProfileFsHelper