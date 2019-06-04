package blended.updater.config

import java.io.File

import scala.collection.immutable._

import blended.launcher.config.LauncherConfig
import blended.util.logging.Logger

/**
 * Convert between [[LauncherConfig]] and [[ResolvedRuntimeConfig]].
 */
trait ConfigConverter {

  private[this] val log = Logger[ConfigConverter]

  def runtimeConfigToLauncherConfig(resolvedRuntimeConfig : ResolvedRuntimeConfig, profileDir : String) : LauncherConfig = {
    import blended.launcher.config.LauncherConfig._

    val bundlePrefix = s"${profileDir}/bundles"
    val runtimeConfig = resolvedRuntimeConfig.runtimeConfig

    val allBundles = resolvedRuntimeConfig.allBundles.
      filter(b => b.startLevel != Some(0)).
      map { bc =>
        BundleConfig(
          location = s"${bundlePrefix}/${bc.jarName.getOrElse(runtimeConfig.resolveFileName(bc.url).get)}",
          start = bc.start,
          startLevel = bc.startLevel.getOrElse(runtimeConfig.defaultStartLevel)
        )
      }.
      distinct

    log.debug(s"Converted bundles: ${allBundles}")

    LauncherConfig(
      frameworkJar = s"${bundlePrefix}/${resolvedRuntimeConfig.framework.jarName.getOrElse(runtimeConfig.resolveFileName(resolvedRuntimeConfig.framework.url).get)}",
      systemProperties = runtimeConfig.systemProperties,
      frameworkProperties = runtimeConfig.frameworkProperties,
      startLevel = runtimeConfig.startLevel,
      defaultStartLevel = runtimeConfig.defaultStartLevel,
      bundles = allBundles,
      branding = runtimeConfig.properties ++ Map(
        RuntimeConfig.Properties.PROFILE_NAME -> runtimeConfig.name,
        RuntimeConfig.Properties.PROFILE_VERSION -> runtimeConfig.version
      )
    )
  }

  def launcherConfigToRuntimeConfig(launcherConfig : LauncherConfig, missingPlaceholder : String) : RuntimeConfig = {
    import RuntimeConfigCompanion._

    RuntimeConfig(
      name = launcherConfig.branding.getOrElse(RuntimeConfig.Properties.PROFILE_NAME, missingPlaceholder),
      version = launcherConfig.branding.getOrElse(RuntimeConfig.Properties.PROFILE_VERSION, missingPlaceholder),
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
