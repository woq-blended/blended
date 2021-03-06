package blended.container.context.impl.internal

import java.io.File
import java.util.Properties

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._
import scala.util.{Success, Try}

import blended.updater.config.Profile
import blended.util.RichTry._
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

class ContainerContextImpl() extends AbstractContainerContextImpl {

  import AbstractContainerContextImpl._

  private[this] lazy val log: Logger = Logger[ContainerContextImpl]
  initialize()

  private def normalizePath(f: File): String = f.getAbsolutePath().replaceAll("\\\\", "/")
  @BeanProperty
  override lazy val containerDirectory: String = normalizePath(new File(System.getProperty("blended.home")))

  @BeanProperty
  override lazy val containerHostname: String = {
    try {
      val localMachine = java.net.InetAddress.getLocalHost()
      localMachine.getCanonicalHostName()
    } catch {
      case _: java.net.UnknownHostException => "UNKNOWN"
    }
  }

  @BeanProperty
  override lazy val containerLogDirectory: String = containerLogDir

  @BeanProperty
  override lazy val profileDirectory: String = profileDir

  lazy val brandingProperties: Map[String, String] = {
    val props: Properties = (try {
      import blended.launcher.runtime.Branding
      // it is possible, that this optional class is not available at runtime,
      // e.g. when started with another launcher
      log.debug("About to read launcher branding properties")
      Option(Branding.getProperties())
    } catch {
      case e: NoClassDefFoundError => None
    }) getOrElse {
      log.warn("Could not read launcher branding properies")
      new Properties()
    }

    val result: Map[String, String] =
      props.entrySet().asScala.map(e => e.getKey().toString() -> e.getValue().toString()).toMap

    log.debug(s"Resolved branding properties : [${result.mkString(",")}]")

    result
  }

  private[this] lazy val profileDir: String = {

    val profileHome =
      brandingProperties.get(Profile.Properties.PROFILE_DIR) orElse {
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
    val f = new File(containerDirectory + "/log")
    normalizePath(f)
  }

  @BeanProperty
  override lazy val containerConfigDirectory: String =
    normalizePath(new File(containerDirectory, CONFIG_DIR))

  @BeanProperty
  override lazy val profileConfigDirectory: String = normalizePath(new File(profileDirectory, CONFIG_DIR))

  override lazy val containerConfig: Config = {
    val sysProps = ConfigFactory.systemProperties()
    val envProps = ConfigFactory.systemEnvironment()

    val cfgFile: File = new File(profileConfigDirectory, "application.conf")
    log.debug(s"Trying to resolve config from [${cfgFile.getAbsolutePath()}]")

    val appCfg: Config =
      ConfigFactory
        .parseFile(cfgFile, ConfigParseOptions.defaults().setAllowMissing(false))
        .withFallback(sysProps)
        .withFallback(envProps)
        .resolve()

    // we need to make sure that all keys are available in the resulting config,
    // even if they point to null values or empty configs
    val allKeys: List[String] = ConfigLocator.fullKeyset("", appCfg)

    val nullKeys = allKeys
      .filter(s => appCfg.getIsNull(s))
      .map(s => (s -> null))
      .toMap
      .asJava

    val emptyKeys = allKeys
      .filter { s =>
        Try { appCfg.getConfig(s) } match {
          case Success(c) => c.isEmpty()
          case _          => false
        }
      }
      .map(s => s -> ConfigFactory.empty().root())
      .toMap
      .asJava

    val evaluated = ConfigLocator.evaluatedConfig(appCfg, this).unwrap

    log.trace(s"After reading application.conf : $evaluated")

    val resolvedCfg: Config = evaluated
      .withFallback(sysProps)
      .withFallback(envProps)
      .withFallback(ConfigFactory.parseMap(nullKeys))
      .withFallback(ConfigFactory.parseMap(emptyKeys))
      .resolve()

    log.debug(s"Resolved container config : $resolvedCfg")

    resolvedCfg
  }
}
