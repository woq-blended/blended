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
import blended.updater.config.LauncherConfig

object Launcher {

  case class InstalledBundle(jarBundle: LauncherConfig.BundleConfig, bundle: Bundle)

  def main(args: Array[String]): Unit = {

    val configFile = args match {
      case Array(configFile) => new File(configFile).getAbsoluteFile()
      case _ =>
        Console.err.println("Usage: main configfile")
        sys.exit(1)
    }

    val launcher = Launcher(configFile)
    val errors = launcher.validate()
    if (!errors.isEmpty) {
      Console.err.println("Could not start the OSGi Framework. Details:\n" + errors.mkString("\n"))
      sys.exit(1)
    }
    launcher.run()
    sys.exit(0)

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
    val files = ("Framework JAR", config.frameworkJar) :: config.bundles.toList.map(b => "Bundle JAR" -> b.location)
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

    config.systemProperties foreach { p =>
      System.setProperty(p._1, p._2)
    }
    val framework = frameworkFactory.newFramework(config.frameworkProperties.asJava)

    val frameworkStartLevel = framework.adapt(classOf[FrameworkStartLevel])
    frameworkStartLevel.setInitialBundleStartLevel(config.defaultStartLevel)

    framework.start()
    log.info(s"Framework started. State: ${framework.getState}")

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
  def run(): Unit = {
    val framework = start()

    def awaitFrameworkStop(framwork: Framework): Unit = {
      val event = framework.waitForStop(0)
      event.getType match {
        case FrameworkEvent.ERROR => log.info("Framework has encountered an error: ", event.getThrowable)
        case FrameworkEvent.STOPPED => log.info("Framework has been stopped by bundle " + event.getBundle)
        case FrameworkEvent.STOPPED_UPDATE => log.info("Framework has been updated by " + event.getBundle + " and need a restart")
        case _ => log.info("Framework stopped. Reason: " + event.getType + " from bundle " + event.getBundle)
      }
    }

    val shutdownHook = new Thread("framework-shutdown-hook") {
      override def run(): Unit = {
        log.info("Catched kill signal: stopping framework")
        framework.stop()
        awaitFrameworkStop(framework)
      }
    }

    try {
      Runtime.getRuntime.addShutdownHook(shutdownHook)
      awaitFrameworkStop(framework)
    } catch {
      case NonFatal(x) =>
        log.error("Framework was interrupted. Cause: ", x)
    } finally {
      Try { Runtime.getRuntime.removeShutdownHook(shutdownHook) }
    }
  }

}
