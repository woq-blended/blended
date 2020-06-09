package blended.updater.config

import java.io.File

import blended.launcher.config.LauncherConfig
import blended.util.logging.Logger

import scala.collection.immutable._

/**
 * Convert between [[LauncherConfig]] and [[ResolvedProfile]].
 */
trait ConfigConverter {

  private[this] val log = Logger[ConfigConverter]

  def runtimeConfigToLauncherConfig(resolvedRuntimeConfig: ResolvedProfile, profileDir: String): LauncherConfig = {
    import blended.launcher.config.LauncherConfig._

    val bundlePrefix = s"${profileDir}/bundles"
    val runtimeConfig = resolvedRuntimeConfig.profile

    val allBundles = resolvedRuntimeConfig.allBundles
      .filter(b => b.startLevel != Some(0))
      .map { bc =>
        BundleConfig(
          location = s"${bundlePrefix}/${bc.jarName.getOrElse(runtimeConfig.resolveFileName(bc.url).get)}",
          start = bc.start,
          startLevel = bc.startLevel.getOrElse(runtimeConfig.defaultStartLevel)
        )
      }
      .distinct

    log.debug(s"Converted bundles: ${allBundles}")

    LauncherConfig(
      frameworkJar = s"${bundlePrefix}/${resolvedRuntimeConfig.framework.jarName
        .getOrElse(runtimeConfig.resolveFileName(resolvedRuntimeConfig.framework.url).get)}",
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
