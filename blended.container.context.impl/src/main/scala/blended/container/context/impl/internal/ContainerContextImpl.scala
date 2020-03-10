package blended.container.context.impl.internal

import java.io.File
import java.util.Properties

import blended.updater.config.{LocalOverlays, OverlayRef, RuntimeConfig}
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

class ContainerContextImpl extends AbstractContainerContextImpl {

  import AbstractContainerContextImpl._

  private[this] lazy val log : Logger = Logger[ContainerContextImpl]

  @BeanProperty
  override lazy val containerDirectory : String =
    new File(System.getProperty("blended.home")).getAbsolutePath

  @BeanProperty
  override lazy val containerHostname : String = {
    try {
      val localMachine = java.net.InetAddress.getLocalHost()
      localMachine.getCanonicalHostName()
    } catch {
      case _ : java.net.UnknownHostException => "UNKNOWN"
    }
  }

  @BeanProperty
  override lazy val containerLogDirectory : String = containerLogDir

  @BeanProperty
  override lazy val profileDirectory : String = profileDir

  lazy val brandingProperties : Map[String, String] = {
    val props : Properties = (try {
      import blended.launcher.runtime.Branding
      // it is possible, that this optional class is not available at runtime,
      // e.g. when started with another launcher
      log.debug("About to read launcher branding properties")
      Option(Branding.getProperties())
    } catch {
      case e : NoClassDefFoundError => None
    }) getOrElse {
      log.warn("Could not read launcher branding properies")
      new Properties()
    }
    props.entrySet().asScala.map(e => e.getKey().toString() -> e.getValue().toString()).toMap
  }

  private[this] lazy val profileDir : String = {

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
      log.error(s"Container directory [${dir}] does not exist.")
    } else if (!configDir.isDirectory() || !configDir.canRead()) {
      log.error(s"Container directory [${dir}] is not readable.")
    }

    val absDir = configDir.getAbsolutePath
    System.setProperty("blended.container.home", absDir)
    absDir
  }

  private[this] lazy val containerLogDir : String = {
    val f = new File(containerDirectory + "/log")
    f.getAbsolutePath()
  }

  @BeanProperty
  override lazy val containerConfigDirectory : String =
    new File(containerDirectory, CONFIG_DIR).getAbsolutePath

  @BeanProperty
  override lazy val profileConfigDirectory : String = new File(profileDirectory, CONFIG_DIR).getAbsolutePath

  override lazy val containerConfig : Config = {
    val sysProps = ConfigFactory.systemProperties()
    val envProps = ConfigFactory.systemEnvironment()

    val overlayConfig = brandingProperties.get(RuntimeConfig.Properties.PROFILE_DIR) match {
      case Some(profileDir) =>
        brandingProperties.get(RuntimeConfig.Properties.OVERLAYS) match {
          case Some(overlays) =>
            val overlayRefs = overlays.split("[,]").toList.map(_.split("[:]", 2)).flatMap {
              case Array(n, v) => Some(OverlayRef(n, v))
              case x =>
                log.debug("Unsupported overlay: " + x.mkString(":"))
                None
            }.toSet
            if (overlayRefs.isEmpty) {
              None
            } else {
              val dir = LocalOverlays.materializedDir(overlayRefs, new File(profileDir))
              val confFile = new File(dir, s"$CONFIG_DIR/application_overlay.conf")
              if (confFile.exists()) {
                log.debug(s"About to read extra application overlay override file: ${confFile}")
                Some(confFile)
              } else {
                None
              }
            }
          case _ => None
        }
      case _ => None
    }

    log.debug(s"Overlay config: ${overlayConfig}")

    val oldCfg : Config = overlayConfig match {
      case Some(oc) => ConfigFactory.parseFile(oc, ConfigParseOptions.defaults().setAllowMissing(false))
      case _        => ConfigFactory.empty()
    }

    val appCfg : Config =
      ConfigLocator.evaluatedConfig(profileConfigDirectory, "application.conf", ConfigFactory.empty(), this)

    oldCfg.withFallback(appCfg)
      .withFallback(sysProps)
      .withFallback(envProps)
      .resolve()
  }

}
