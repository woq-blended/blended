package blended.container.context.internal

import java.io.File
import java.util.Properties

import blended.container.context.ContainerContext
import blended.updater.config.{LocalOverlays, OverlayRef, RuntimeConfig}
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object ContainerContextImpl {
  private val PROP_BLENDED_HOME = "blended.home"
  private val CONFIG_DIR = "etc"
}

class ContainerContextImpl() extends ContainerContext {

  import ContainerContextImpl._

  private[this] val log = LoggerFactory.getLogger(classOf[ContainerContextImpl])

  override def getContainerDirectory() = new File(System.getProperty("blended.home")).getAbsolutePath

  override def getContainerHostname(): String = {
    try {
      val localMachine = java.net.InetAddress.getLocalHost()
      localMachine.getCanonicalHostName()
    } catch {
      case uhe: java.net.UnknownHostException => "UNKNOWN"
    }
  }

  override def getContainerLogDirectory(): String = containerLogDir

  override def getProfileDirectory(): String = profileDir

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

  private[this] lazy val profileDir: String = {

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

  private[this] lazy val containerLogDir: String = {
    val f = new File(getContainerDirectory() + "/log")
    f.getAbsolutePath()
  }


  override def getContainerConfigDirectory() = new File(getContainerDirectory(), CONFIG_DIR).getAbsolutePath

  override def getProfileConfigDirectory(): String = new File(getProfileDirectory(), CONFIG_DIR).getAbsolutePath

  private[this] lazy val ctConfig : Config = {
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
      new File(getProfileConfigDirectory(), "application.conf"), ConfigParseOptions.defaults().setAllowMissing(false)
    )).
      withFallback(sysProps).
      withFallback(envProps).
      resolve()
  }

  override def getContainerConfig() = ctConfig
}
