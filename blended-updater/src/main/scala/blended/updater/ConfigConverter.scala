package blended.updater

import blended.launcher.LauncherConfig
import blended.launcher.{ BundleConfig => LauncherBundleConfig }

trait ConfigConverter {

  def convertToLauncherConfig(runtimeConfig: RuntimeConfig, baseDir: String): LauncherConfig = {

    val baseDirPrefix = s"${baseDir}/${runtimeConfig.name}-${runtimeConfig.version}/"

    val bundles = runtimeConfig.bundles.map { bc =>
      LauncherBundleConfig(
        location = baseDirPrefix + bc.jarName,
        start = bc.start,
        startLevel = bc.startLevel.getOrElse(runtimeConfig.defaultStartLevel))
    }

    LauncherConfig(
      frameworkJar = baseDirPrefix + runtimeConfig.framework.jarName,
      systemProperties = runtimeConfig.systemProperties,
      frameworkProperties = runtimeConfig.frameworkProperties,
      startLevel = runtimeConfig.startLevel,
      defaultStartLevel = runtimeConfig.defaultStartLevel,
      bundles = bundles,
      branding = Map(
        "profile.name" -> runtimeConfig.name,
        "profile.version" -> runtimeConfig.version
      )
    )
  }

}

object ConfigConverter extends ConfigConverter