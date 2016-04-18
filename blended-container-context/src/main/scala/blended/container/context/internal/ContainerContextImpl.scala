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
package blended.container.context.internal

import java.io.{BufferedInputStream, File, FileInputStream, FileNotFoundException, FileOutputStream, IOException}
import java.util.Properties
import blended.container.context.ContainerContext
import blended.launcher.BrandingProperties
import blended.updater.config.LocalOverlays
import blended.updater.config.OverlayRef
import blended.updater.config.RuntimeConfig
import com.typesafe.config.ConfigParseOptions
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object ContainerContextImpl {
  private val PROP_BLENDED_HOME = "blended.home"
  private val CONFIG_DIR = "etc"
}

class ContainerContextImpl() extends ContainerContext {

  import ContainerContextImpl._

  private[this] val log = LoggerFactory.getLogger(classOf[ContainerContextImpl])

  override def getContainerHostname(): String = {
    try {
      val localMachine = java.net.InetAddress.getLocalHost()
      localMachine.getCanonicalHostName()
    } catch {
      case uhe: java.net.UnknownHostException => "UNKNOWN"
    }
  }

  override def getContainerDirectory(): String = containerDir

  val brandingProperties: Map[String, String] = {
    val props = (try {
      import blended.launcher.runtime.Branding
      // it is possible, that this optional class is not available at runtime,
      // e.g. when started with another launcher
      log.debug("About to read launcher branding properies")
      Option(Branding.getProperties())
    } catch {
      case e: NoClassDefFoundError => None
    }) getOrElse {
      log.warn("Could not read launcher branding properies")
      new Properties()
    }
    props.entrySet().asScala.map(e => e.getKey().toString() -> e.getValue().toString()).toMap
  }

  private[this] val containerDir: String = {

    val profileHome =
      brandingProperties.get(RuntimeConfig.Properties.PROFILE_DIR) orElse {
        log.warn("Could not read the profile directory from read launcher branding properties")
        None
      }

    val dir = profileHome getOrElse {
      Option(System.getProperty(PROP_BLENDED_HOME)) getOrElse {
        Option(System.getProperty("user.dir")) getOrElse {
          "."
        }
      }
    }
    val configDir = new File(dir)

    if (!configDir.exists()) {
      log.error("Container directory [{}] does not exist.", dir)
    } else if (!configDir.isDirectory() || !configDir.canRead()) {
      log.error("Container directory [{}] is not readable.", dir)
    }

    val absDir = configDir.getAbsolutePath
    System.setProperty("blended.container.home", absDir)
    absDir
  }

  override def getContainerConfigDirectory(): String = new File(getContainerDirectory(), CONFIG_DIR).getPath

  override def getContainerConfig(): Config = {
    val sysProps = ConfigFactory.systemProperties()
    val envProps = ConfigFactory.systemEnvironment()

    val branding = brandingProperties
    val overlayConfig = branding.get(RuntimeConfig.Properties.PROFILE_DIR) match {
      case Some(profileDir) =>
        branding.get(RuntimeConfig.Properties.OVERLAYS) match {
          case Some(overlays) =>
            val overlayRefs = overlays.split("[,]").toList.map(_.split("[:]", 2)).flatMap {
              case Array(n, v) => Some(OverlayRef(n, v))
              case x =>
                log.debug("Unsupported overlay: " + x.mkString(":"))
                None
            }.toSet
            if (overlayRefs.isEmpty) None
            else {
              val dir = LocalOverlays.materializedDir(overlayRefs, new File(profileDir))
              val confFile = new File(dir, "etc/application_overlay.conf")
              if (confFile.exists()) {
                log.debug(s"About to read extra application overlay override file: ${confFile}")
                Some(confFile)
              } else None
            }
          case _ => None
        }
      case _ => None
    }

    log.debug("Overlay config: {}", overlayConfig)

    val config = overlayConfig match {
      case Some(oc) => ConfigFactory.parseFile(oc, ConfigParseOptions.defaults().setAllowMissing(false))
      case _ => ConfigFactory.empty()
    }
    config.withFallback(ConfigFactory.parseFile(
      new File(getContainerConfigDirectory, "application.conf"), ConfigParseOptions.defaults().setAllowMissing(false)
    )).
      withFallback(sysProps).
      withFallback(envProps).
      resolve()
  }

  @deprecated
  override def readConfig(configId: String): Properties = {

    def resolveSystemProps(in: Properties): Properties = {

      val result = new Properties()

      in.propertyNames().asScala.foreach { k =>
        val key = k.toString()
        var value = in.getProperty(key)

        val regex = "[^\\$]*\\$\\{([^}]*)}".r

        regex.findAllIn(value).matchData.map(_.group(1)).foreach { m =>
          Option(System.getProperty(m)) match {
            case Some(sysProp) => value = value.replaceFirst("\\$\\{" + m + "}", sysProp)
            case None =>
          }
        }
        result.setProperty(key, value)
      }

      result

    }

    val props = new Properties()
    val f = new File(getConfigFile(configId))

    if (!f.exists() || f.isDirectory() || !f.canRead()) {
      log.warn("Cannot open [{}]", f.getAbsolutePath())
      return props
    }

    try {
      val is = new BufferedInputStream(new FileInputStream(f))
      try {
        props.load(is)
      } catch {
        case e: IOException =>
          log.warn("Error reading config file: {}", Array(f, e): _*)
      } finally {
        is.close()
      }
    } catch {
      case e: FileNotFoundException =>
        log.warn("Could not find config file: {}", Array(f, e): _*)

    }

    log.info("Read [{}] properties from [{}]", props.size(), f.getAbsolutePath())
    resolveSystemProps(props)
  }

  @deprecated
  override def writeConfig(configId: String, props: Properties): Unit = {
    val configFile = new File(getConfigFile(configId))

    log.debug("Wrting config for [{}] to [{}].", Array(configId, configFile): _*)

    Option(configFile.getParentFile).filter(!_.exists()).foreach { p =>
      log.debug("Creating missing config directory: {}", p)
      p.mkdirs()
    }

    try {
      val os = new FileOutputStream(configFile)
      try {
        props.store(os, "")
      } catch {
        case e: IOException =>
          log.warn("Error writing config file: {}", Array(configFile, e): _*)
      } finally {
        os.close()
      }
    } catch {
      case e: FileNotFoundException =>
        log.warn("Could not find config file: {}", Array(configFile, e): _*)
    }

    log.info("Exported configuration [{}]", configFile)
  }

  private def getConfigFile(configId: String): String = new File(getContainerConfigDirectory(), s"$configId.cfg").getPath()
}
