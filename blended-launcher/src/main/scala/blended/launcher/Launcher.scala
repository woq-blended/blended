/*
 * Copyright 2014ff,  https://github.com/woq-blended
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package blended.launcher

import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.immutable.Seq
import scala.collection.immutable.Map
import scala.util.Try
import scala.util.control.NonFatal
import org.osgi.framework.Bundle
import org.osgi.framework.Constants
import org.osgi.framework.FrameworkEvent
import org.osgi.framework.FrameworkListener
import org.osgi.framework.launch.Framework
import org.osgi.framework.launch.FrameworkFactory
import org.osgi.framework.startlevel.BundleStartLevel
import org.osgi.framework.startlevel.FrameworkStartLevel
import org.osgi.framework.wiring.FrameworkWiring
import blended.launcher.internal.Logger
import blended.launcher.config.LauncherConfig
import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser
import de.tototec.cmdoption.CmdlineParserException
import blended.updater.config.ConfigConverter
import com.typesafe.config.ConfigFactory
import blended.updater.config.RuntimeConfig
import java.util.Properties
import blended.updater.config.ProfileLookup
import blended.launcher.runtime.Branding
import java.util.Hashtable
import blended.updater.config.LocalRuntimeConfig
import scala.util.Failure
import scala.util.Success
import com.typesafe.config.ConfigParseOptions

object Launcher {

  case class InstalledBundle(jarBundle: LauncherConfig.BundleConfig, bundle: Bundle)

  class Cmdline {

    @CmdOption(names = Array("--config", "-c"), args = Array("FILE"),
      description = "Configuration file",
      conflictsWith = Array("--profile", "--profile-lookup")
    )
    def setPonfigFile(file: String): Unit = configFile = Option(file)
    var configFile: Option[String] = None

    @CmdOption(names = Array("--help", "-h"), description = "Show this help", isHelp = true)
    var help: Boolean = false

    @CmdOption(names = Array("--profile", "-p"), args = Array("profile"),
      description = "Start the profile from file or directory {0}",
      conflictsWith = Array("--profile-lookup", "--config")
    )
    def setProfileDir(dir: String): Unit = profileDir = Option(dir)
    var profileDir: Option[String] = None

    @CmdOption(names = Array("--framework-restart", "-r"), args = Array("BOOLEAN"),
      description = "Should the launcher restart the framework after updates." +
        " If disabled and the framework was updated, the exit code is 2.")
    var handleFrameworkRestart: Boolean = true

    @CmdOption(names = Array("--profile-lookup", "-P"), args = Array("configfile"),
      description = "Lookup to profile file or directory from the config file {0}",
      conflictsWith = Array("--profile", "--config")
    )
    def setProfileLookup(file: String): Unit = profileLookup = Option(file)
    var profileLookup: Option[String] = None

    @CmdOption(names = Array("--reset-profile-props"),
      description = "Try to recreate the profile properties file before starting a profile",
      conflictsWith = Array("--config")
    )
    var resetProfileProps: Boolean = false
  }

  def main(args: Array[String]): Unit = {
    try {
      run(args)
    } catch {
      case t: LauncherException =>
        Console.err.println(s"Error: ${t.getMessage()}")
        sys.exit(t.errorCode)
      case t: Throwable =>
        Console.err.println(s"Error: ${t.getMessage()}")
        //        sys.exit(1)
        throw t
    }
    sys.exit(0)
  }

  def run(args: Array[String]): Unit = {

    val log = Logger[Launcher.type]

    val cmdline = new Cmdline()
    val cp = new CmdlineParser(cmdline)
    try {
      cp.parse(args: _*)
    } catch {
      case e: CmdlineParserException =>
        sys.error(s"${e.getMessage()}\nRun launcher --help for help.")
    }

    if (cmdline.help) {
      cp.usage()
      return
    }

    val handleFrameworkRestart = cmdline.handleFrameworkRestart

    var firstStart = true
    var retVal: Int = 0
    do {
      case class Configs(launcherConfig: LauncherConfig, profileConfig: Option[LocalRuntimeConfig] = None)

      val configs = cmdline.configFile match {
        case Some(configFile) =>
          val config = ConfigFactory.parseFile(new File(configFile), ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
          Configs(LauncherConfig.read(config))
        case None =>
          val profileLookup = cmdline.profileLookup.map { pl =>
            log.debug("About to read profile lookup file: " + pl)
            val c = ConfigFactory.parseFile(new File(pl), ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
            ProfileLookup.read(c).map { pl =>
              pl.copy(profileBaseDir = pl.profileBaseDir.getAbsoluteFile())
            }.get
          }

          val profile = profileLookup match {
            case Some(pl) =>
              s"${pl.profileBaseDir}/${pl.profileName}/${pl.profileVersion}"
            case None =>
              cmdline.profileDir match {
                case Some(profile) => profile
                case None =>
                  sys.error("Either a config file or a profile dir or file or a profile lookup path must be given")
              }
          }

          val (profileDir, profileFile) = if (new File(profile).isDirectory()) {
            profile -> new File(profile, "profile.conf")
          } else {
            Option(new File(profile).getParent()).getOrElse(".") -> new File(profile)
          }
          val config = ConfigFactory.parseFile(profileFile, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
          val runtimeConfig = RuntimeConfig.read(config).get
          val launchConfig = ConfigConverter.runtimeConfigToLauncherConfig(runtimeConfig, profileDir)

          var brandingProps = Map(
            RuntimeConfig.Properties.PROFILE_DIR -> profileDir
          )
          profileLookup.foreach { pl =>
            brandingProps ++= Map(
              RuntimeConfig.Properties.PROFILE_LOOKUP_FILE -> new File(cmdline.profileLookup.get).getAbsolutePath(),
              RuntimeConfig.Properties.PROFILES_BASE_DIR -> pl.profileBaseDir.getAbsolutePath()
            )
          }

          Configs(
            launcherConfig = launchConfig.copy(branding = launchConfig.branding ++ brandingProps),
            profileConfig = Some(LocalRuntimeConfig(runtimeConfig, new File(profileDir))))
      }

      val launcher = new Launcher(configs.launcherConfig)
      val errors = launcher.validate()
      if (!errors.isEmpty) sys.error("Could not start the OSGi Framework. Details:\n" + errors.mkString("\n"))

      if (firstStart && cmdline.resetProfileProps) {
        val localConfig = configs.profileConfig.getOrElse(sys.error("Cannot reset profile properties file. Profile unknown!"))
        RuntimeConfig.createPropertyFile(localConfig, None) match {
          case None => // nothing to generate, ok
          case Some(Success(f)) => // generated successfully, ok
            Console.err.println(s"Created properties file for profile: ${f}")
          case Some(Failure(e)) => sys.error(s"Could not reset properties file. ${e.getMessage()}")
        }
      } else {
        // check props
        configs.profileConfig.foreach { localConfig =>
          // TODO: check if all mandatory props are set
        }
      }

      retVal = launcher.run()

      firstStart = false
    } while (handleFrameworkRestart && retVal == 2)

    if (retVal != 0) throw new LauncherException("", errorCode = retVal)
  }

  def apply(configFile: File): Launcher = new Launcher(LauncherConfig.read(configFile))

}

class Launcher private (config: LauncherConfig) {
  import Launcher._

  private[this] val log = Logger[Launcher]

  /**
   * Validate this Launcher's configuration and return the issues if any found.
   */
  def validate(): Seq[String] = {
    val files = ("Framework JAR", config.frameworkJar) ::
      config.bundles.toList.map(b => "Bundle JAR" -> b.location)
    files.flatMap {
      case (kind, file) =>
        val f = new File(file).getAbsoluteFile()
        if (!f.exists()) Some(s"${kind} ${f} does not exists")
        else if (!f.isFile()) Some(s"${kind} ${f} is not a file")
        else if (!f.canRead()) Some(s"${kind} ${f} is not readable")
        else None
    }

  }

  /**
   * Run an (embedded) OSGiFramework based of this Launcher's configuration.
   */
  def start(): Framework = {
    log.info(s"Starting OSGi framework based on config: ${config}");

    val frameworkURL = new File(config.frameworkJar).getAbsoluteFile.toURI().normalize().toURL()
    log.info("Framework Bundle from: " + frameworkURL)
    if (!new File(frameworkURL.getFile()).exists) throw new RuntimeException("Framework Bundle does not exist")
    val cl = new URLClassLoader(Array(frameworkURL), getClass.getClassLoader)
    val frameworkFactory = ServiceLoader.load(classOf[FrameworkFactory], cl).iterator().next()

    val brandingProps = {
      val brandingProps = new Properties()
      config.branding.foreach { case (k, v) => brandingProps.setProperty(k, v) }
      BrandingProperties.setLastBrandingProperties(brandingProps)
      log.debug("Exposing branding via class " + classOf[BrandingProperties].getName() + ": " + brandingProps)
      brandingProps
    }

    config.systemProperties foreach { p =>
      System.setProperty(p._1, p._2)
    }
    val framework = frameworkFactory.newFramework(config.frameworkProperties.asJava)

    val frameworkStartLevel = framework.adapt(classOf[FrameworkStartLevel])
    frameworkStartLevel.setInitialBundleStartLevel(config.defaultStartLevel)

    framework.start()
    log.info(s"Framework started. State: ${framework.getState}")

    {
      val props = new Hashtable[String, AnyRef]()
      props.put("blended.launcher", "true")
      framework.getBundleContext.registerService(classOf[Properties], brandingProps, props)
    }

    log.info("Installing bundles");
    val context = framework.getBundleContext()
    val osgiBundles = config.bundles.map { b =>
      log.info(s"Installing Bundle: ${b}")
      // TODO: What happens here, if the JAR is not a bundle?
      val osgiBundle = context.installBundle(new File(b.location).getAbsoluteFile.toURI().normalize().toString())
      log.info("Bundle installed: " + b)
      val bundleStartLevel = osgiBundle.adapt(classOf[BundleStartLevel])
      log.debug(s"Setting start level for bundle ${osgiBundle.getSymbolicName()} to ${b.startLevel}")
      bundleStartLevel.setStartLevel(b.startLevel)
      InstalledBundle(b, osgiBundle)
    }
    log.info(s"${osgiBundles.size} bundles installed")

    def isFragment(b: InstalledBundle) = b.bundle.getHeaders.get(Constants.FRAGMENT_HOST) != null

    1.to(config.startLevel).map { startLevel =>
      frameworkStartLevel.setStartLevel(startLevel, new FrameworkListener() {
        override def frameworkEvent(event: FrameworkEvent): Unit = {
          log.debug(s"Active start level ${startLevel} reached")
        }
      })

      val bundlesToStart = osgiBundles.filter(b => b.jarBundle.startLevel == startLevel
        && b.jarBundle.start && !isFragment(b))

      log.info(s"Starting ${bundlesToStart.size} bundles");

      val startedBundles = bundlesToStart.map { bundle =>
        val result = Try {
          bundle.bundle.start()
        }
        log.info(s"State of ${bundle.bundle.getSymbolicName}: ${bundle.bundle.getState}")
        bundle -> result
      }
      log.info(s"${startedBundles.filter(_._2.isSuccess).size} bundles started");

      val failedBundles = startedBundles.filter(_._2.isFailure)

      if (!failedBundles.isEmpty) {
        log.warn(s"Could not start some bundles:\n${
          failedBundles.map(failed => s"\n - ${failed._1}\n ---> ${failed._2}")
        }")
      }
    }

    val bundlesInInstalledState = osgiBundles.filter(_.bundle.getState() == Bundle.INSTALLED)
    if (!bundlesInInstalledState.isEmpty) {
      log.debug(s"The following bundles are in installed state: ${bundlesInInstalledState.map(b => s"${b.bundle.getSymbolicName}-${b.bundle.getVersion}")}")
      log.info("Resolving installed bundles")
      val frameworkWiring = framework.adapt(classOf[FrameworkWiring])
      frameworkWiring.resolveBundles(null /* all bundles */ )
      val secondAttemptInstalled = osgiBundles.filter(_.bundle.getState() == Bundle.INSTALLED)
      log.debug(s"The following bundles are in installed state: ${secondAttemptInstalled.map(b => s"${b.bundle.getSymbolicName}-${b.bundle.getVersion}")}")
    }

    log.info("Laucher finished starting of framework and bundles. Awaiting framework termination now.")
    // Framework and bundles started

    framework
  }

  /**
   * Run an (embedded) OSGiFramework based of this Launcher's configuration.
   */
  def run(): Int = {
    val framework = start()

    def awaitFrameworkStop(framwork: Framework): Int = {
      val event = framework.waitForStop(0)
      event.getType match {
        case FrameworkEvent.ERROR =>
          log.info("Framework has encountered an error: ", event.getThrowable)
          1
        case FrameworkEvent.STOPPED =>
          log.info("Framework has been stopped by bundle " + event.getBundle)
          0
        case FrameworkEvent.STOPPED_UPDATE =>
          log.info("Framework has been updated by " + event.getBundle + " and need a restart")
          2
        case _ =>
          log.info("Framework stopped. Reason: " + event.getType + " from bundle " + event.getBundle)
          0
      }
    }

    val shutdownHook = new Thread("framework-shutdown-hook") {
      override def run(): Unit = {
        log.info("Catched kill signal: stopping framework")
        framework.stop()
        awaitFrameworkStop(framework)
        BrandingProperties.setLastBrandingProperties(new Properties())
      }
    }

    try {
      Runtime.getRuntime.addShutdownHook(shutdownHook)
      awaitFrameworkStop(framework)
    } catch {
      case NonFatal(x) =>
        log.error("Framework was interrupted. Cause: ", x)
        1
    } finally {
      BrandingProperties.setLastBrandingProperties(new Properties())
      Try { Runtime.getRuntime.removeShutdownHook(shutdownHook) }
    }
  }

}
