package blended.updater.config

import java.io.File

import blended.launcher.config.LauncherConfig
import blended.util.logging.Logger

import scala.collection.immutable._
import scala.util.Try

/**
 * Convert between [[LauncherConfig]] and [[ResolvedProfile]].
 */
trait ConfigConverter {

  private[this] val log = Logger[ConfigConverter]

  def runtimeConfigToLauncherConfig(resolvedRuntimeConfig: ResolvedProfile, profileDir: String): Try[LauncherConfig] = Try {
    import blended.launcher.config.LauncherConfig._

    val bundleDir : String = s"${profileDir}/bundles"
    val runtimeConfig : Profile = resolvedRuntimeConfig.profile

    val allBundles = resolvedRuntimeConfig.allBundles.get
      .filter(b => b.startLevel != Some(0))
      .map { bc =>
        BundleConfig(
          location = s"${bundleDir}/${bc.jarName.getOrElse(runtimeConfig.resolveFileName(bc.url).get)}",
          start = bc.start,
          startLevel = bc.startLevel.getOrElse(runtimeConfig.defaultStartLevel)
        )
      }
      .distinct

    log.debug(s"Converted bundles: ${allBundles}")

    LauncherConfig(
      frameworkJar = s"${bundleDir}/${resolvedRuntimeConfig.framework.get.jarName
        .getOrElse(runtimeConfig.resolveFileName(resolvedRuntimeConfig.framework.get.url).get)}",
      systemProperties = runtimeConfig.systemProperties,
      frameworkProperties = runtimeConfig.frameworkProperties,
      startLevel = runtimeConfig.startLevel,
      defaultStartLevel = runtimeConfig.defaultStartLevel,
      bundles = allBundles,
      branding = runtimeConfig.properties ++ Map(
        Profile.Properties.PROFILE_NAME -> runtimeConfig.name,
        Profile.Properties.PROFILE_VERSION -> runtimeConfig.version
      )
    )
  }

  def launcherConfigToRuntimeConfig(launcherConfig: LauncherConfig, missingPlaceholder: String): Profile = {
    import ProfileCompanion._

    Profile(
      name = launcherConfig.branding.getOrElse(Profile.Properties.PROFILE_NAME, missingPlaceholder),
      version = launcherConfig.branding.getOrElse(Profile.Properties.PROFILE_VERSION, missingPlaceholder),
      startLevel = launcherConfig.startLevel,
      defaultStartLevel = launcherConfig.defaultStartLevel,
      frameworkProperties = launcherConfig.frameworkProperties,
      systemProperties = launcherConfig.systemProperties,
      bundles = BundleConfig(
        url = missingPlaceholder,
        jarName = new File(launcherConfig.frameworkJar).getName(),
        sha1Sum = digestFile(new File(launcherConfig.frameworkJar)).orNull,
        start = true,
        startLevel = 0
      ) +:
        launcherConfig.bundles.toList.map { b =>
        BundleConfig(
          url = missingPlaceholder,
          jarName = new File(b.location).getName(),
          sha1Sum = digestFile(new File(b.location)).orNull,
          start = b.start,
          startLevel = b.startLevel
        )
      },
      properties = launcherConfig.branding
    )
  }

}

object ConfigConverter extends ConfigConverter
