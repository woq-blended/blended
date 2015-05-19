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
import scala.collection.JavaConverters._
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
import blended.launcher.internal.Logger
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions

object Launcher {

  def main(args: Array[String]): Unit = {

    val configFile = args match {
      case Array(configFile) => new File(configFile).getAbsoluteFile()
      case _ =>
        Console.err.println("Usage: main configfile")
        sys.exit(1)
    }

    new Launcher(LauncherConfig.read(configFile)).run()
    sys.exit(0)

  }

}

case class BundleConfig(location: String, start: Boolean = false, startLevel: Int)

case class LauncherConfig(
  frameworkJar: String,
  systemProperties: Map[String, String],
  frameworkProperties: Map[String, String],
  startLevel: Int,
  defaultStartLevel: Int,
  bundles: Seq[BundleConfig])

object LauncherConfig {

  private[this] val log = Logger[LauncherConfig.type]

  def read(file: File): LauncherConfig = {

    val config = ConfigFactory.parseFile(file).getConfig("de.wayofquality.blended.launcher.Launcher").resolve()

    val reference = ConfigFactory.parseResources(getClass(), "LauncherConfig-reference.conf",
      ConfigParseOptions.defaults().setAllowMissing(false))
    log.debug(s"Checking config with reference: ${reference}")
    config.checkValid(reference)

    LauncherConfig(
      frameworkJar = config.getString("frameworkBundle"),
      systemProperties = config.getConfig("systemProperties")
        .entrySet().asScala.map {
          entry => entry.getKey() -> entry.getValue().unwrapped().asInstanceOf[String]
        }.toMap,
      frameworkProperties = config.getConfig("frameworkProperties")
        .entrySet().asScala.map {
          entry => entry.getKey() -> entry.getValue().unwrapped().asInstanceOf[String]
        }.toMap,
      startLevel = config.getInt("startLevel"),
      defaultStartLevel = config.getInt("defaultStartLevel"),
      bundles = config.getObjectList("bundles")
        .asScala.map { b =>
          val c = b.toConfig()
          BundleConfig(
            location = c.getString("location"),
            start = if (c.hasPath("start")) c.getBoolean("start") else false,
            startLevel = if (c.hasPath("startLevel")) c.getInt("startLevel") else config.getInt("defaultStartLevel")
          )
        }.toList
    )

  }

}

class Launcher(config: LauncherConfig) {
  import Launcher._

  private[this] val log = Logger[Launcher]

  case class InstalledBundle(jarBundle: BundleConfig, bundle: Bundle)

  def run(): Unit = {
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

    framework.start
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

    log.info("Laucher finished starting of framework and bundles. Awaiting framework termination now.")
    // Framework and bundles started

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
