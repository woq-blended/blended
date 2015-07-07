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

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.util.Properties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import blended.container.context.ContainerContext
import scala.io.Source
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.io.IOException
import scala.util.Try

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

  private[this] val containerDir: String = {

    val profileHome = Try {
      import blended.launcher.runtime.Branding
      // it is possible, that this optional class is not available at runtime, 
      // e.g. when started with another launcher
      log.debug("About to read launcher branding properies")
      Branding.getProperties()
    }.toOption.flatMap(ps => Option(ps.getProperty("blended.updater.profile.dir"))).
      orElse {
        log.warn("Could not read the profile directory from read launcher branding properies")
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

    configDir.getAbsolutePath()
  }

  override def getContainerConfigDirectory(): String = new File(getContainerDirectory(), CONFIG_DIR).getPath

  override def readConfig(configId: String): Properties = {
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
    props
  }

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

  private def getConfigFile(configId: String): String = new File(getContainerConfigDirectory(), s"${configId}.cfg").getPath()
}
