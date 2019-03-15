package blended.container.context.impl.internal

import java.io.File
import java.util.Properties

import scala.collection.JavaConverters._
import blended.container.context.api.{ContainerContext, ContainerCryptoSupport}
import blended.updater.config.{LocalOverlays, OverlayRef, RuntimeConfig}
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

import scala.io.Source
import scala.util.Try

object ContainerContextImpl {
  private val PROP_BLENDED_HOME = "blended.home"
  private val CONFIG_DIR = "etc"
  private val SECRET_FILE_PATH  : String = "blended.security.secretFile"

}

class ContainerContextImpl() extends ContainerContext {

  import ContainerContextImpl._

  private[this] val log = Logger[ContainerContextImpl]

  override def getContainerDirectory() : String =
    new File(System.getProperty("blended.home")).getAbsolutePath

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
      log.error(s"Container directory [${dir}] does not exist.")
    } else if (!configDir.isDirectory() || !configDir.canRead()) {
      log.error(s"Container directory [${dir}] is not readable.")
    }

    val absDir = configDir.getAbsolutePath
    System.setProperty("blended.container.home", absDir)
    absDir
  }

  private[this] lazy val containerLogDir: String = {
    val f = new File(getContainerDirectory() + "/log")
    f.getAbsolutePath()
  }

  private lazy val cryptoSupport : ContainerCryptoSupport = {
    val ctConfig : Config = getContainerConfig()

    val cipherSecretFile : String = if (ctConfig.hasPath(SECRET_FILE_PATH)) {
      ctConfig.getString(SECRET_FILE_PATH)
    } else {
      "secret"
    }

    val secretFromFile =
      Source.fromFile(
        new File(getContainerConfigDirectory(), cipherSecretFile)
      ).getLines().toList.headOption.getOrElse("vczP26-QZ5n%$8YP")

    val secret : String = secretFromFile + "V*YE6FPXW6#!g^hD"

    new ContainerCryptoSupportImpl(secret, "AES")
  }

  override def getContainerCryptoSupport(): ContainerCryptoSupport = cryptoSupport

  override def getContainerConfigDirectory(): String =
    new File(getContainerDirectory(), CONFIG_DIR).getAbsolutePath

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

    val config = overlayConfig match {
      case Some(oc) => ConfigFactory.parseFile(oc, ConfigParseOptions.defaults().setAllowMissing(false))
      case _ => ConfigFactory.empty()
    }
    config.withFallback(ConfigFactory.parseFile(
      new File(getProfileConfigDirectory(), "application.conf"), ConfigParseOptions.defaults().setAllowMissing(false)
    ))
    .withFallback(sysProps)
    .withFallback(envProps)
    .resolve()
  }

  override def getContainerConfig() : Config = ctConfig

}
