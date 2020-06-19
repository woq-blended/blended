package blended.updater

import java.io.File

import scala.util.Try

import blended.updater.config._
import blended.util.logging.Logger
import com.typesafe.config.{ConfigFactory, ConfigParseOptions}

class ProfileFsHelper {

  private[this] val log = Logger[ProfileFsHelper]

  def scanForRuntimeConfigs(installBaseDir: File): List[LocalProfile] = {
    log.debug(s"Scanning for runtime configs in ${installBaseDir}")

    val configFiles = Option(installBaseDir.listFiles).getOrElse(Array()).toList.flatMap { nameDir =>
      Option(nameDir.listFiles).getOrElse(Array()).toList.flatMap { versionDir =>
        val profileFile = new File(versionDir, "profile.conf")
        if (profileFile.exists()) Some(profileFile)
        else None
      }
    }

    log.debug(s"Found potential runtime config files: ${configFiles}")

    // read configs
    val runtimeConfigs = configFiles.flatMap { runtimeConfigFile =>
      Try {
        val versionDir = runtimeConfigFile.getParentFile()
        val version = versionDir.getName()
        val name = versionDir.getParentFile.getName()

        val config =
          ConfigFactory.parseFile(runtimeConfigFile, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
        val resolved = ResolvedProfile(ProfileCompanion.read(config).get)
        val local = LocalProfile(baseDir = versionDir, resolvedProfile = resolved)

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

  def scanForProfiles(installBaseDir: File,
                      runtimeConfigs: Option[List[LocalProfile]] = None): List[StatefulLocalProfile] = {
    log.debug(s"Scanning for profiles in: ${installBaseDir}")

    val rcs = runtimeConfigs.getOrElse(scanForRuntimeConfigs(installBaseDir))

    val runtimeConfigsWithIssues = rcs.flatMap { localConfig =>
      val issues = localConfig
        .validate(
          includeResourceArchives = false,
          explodedResourceArchives = true
        )
        .toList
      log.debug(
        s"Runtime config ${localConfig.runtimeConfig.name}-${localConfig.runtimeConfig.version} issues: ${issues}")
      List(localConfig -> issues)

    }

    log.debug(s"Runtime configs (with issues): ${runtimeConfigsWithIssues}")

    def profileState(issues: List[String]): StatefulLocalProfile.ProfileState = issues match {
      case Seq()  => StatefulLocalProfile.Staged
      case issues => StatefulLocalProfile.Pending(issues)
    }

    val fullProfiles = runtimeConfigsWithIssues.flatMap {
      case (localRuntimeConfig, issues) => List(StatefulLocalProfile(localRuntimeConfig, profileState(issues)))
    }

    fullProfiles
  }

}

object ProfileFsHelper extends ProfileFsHelper
