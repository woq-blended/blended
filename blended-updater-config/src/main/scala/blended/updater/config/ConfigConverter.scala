package blended.updater.config

import java.io.File
import scala.collection.immutable._

trait ConfigConverter {
  
  import ConfigConverter._

  def runtimeConfigToLauncherConfig(runtimeConfig: RuntimeConfig, profileDir: String): LauncherConfig = {
    import LauncherConfig._

    val baseDirPrefix = s"${profileDir}/"

    val allBundles = runtimeConfig.allBundles.
      filter(b => b.startLevel != Some(0)).
      map { bc =>
        BundleConfig(
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
      bundles = allBundles,
      branding = Map(
        "profile.name" -> runtimeConfig.name,
        "profile.version" -> runtimeConfig.version
      )
    )
  }

  def launcherConfigToRuntimeConfig(launcherConfig: LauncherConfig, missingPlaceholder: String): RuntimeConfig = {
    import RuntimeConfig._
    RuntimeConfig(
      name = launcherConfig.branding.getOrElse("profile.name", missingPlaceholder),
      version = launcherConfig.branding.getOrElse("profile.version", missingPlaceholder),
      startLevel = launcherConfig.startLevel,
      defaultStartLevel = launcherConfig.defaultStartLevel,
      frameworkProperties = launcherConfig.frameworkProperties,
      systemProperties = launcherConfig.systemProperties,
      bundles = BundleConfig(
        url = missingPlaceholder,
        jarName = new File(launcherConfig.frameworkJar).getName(),
        sha1Sum = digestFile(new File(launcherConfig.frameworkJar)).getOrElse(missingPlaceholder),
        start = true,
        startLevel = Some(0)
      ) +:
        launcherConfig.bundles.toList.map { b =>
          BundleConfig(
            url = missingPlaceholder,
            jarName = new File(b.location).getName(),
            sha1Sum = digestFile(new File(b.location)).getOrElse(missingPlaceholder),
            start = b.start,
            startLevel = Option(b.startLevel)
          )
        },
      fragments = Seq()
    )
  }

}

object ConfigConverter extends ConfigConverter