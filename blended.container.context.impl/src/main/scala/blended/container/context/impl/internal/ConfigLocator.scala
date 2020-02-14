package blended.container.context.impl.internal

import java.io.File

import blended.container.context.api.ContainerContext
import blended.util.logging.Logger
import com.typesafe.config._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
 * A helper class to read HOCON config files from the file system and apply blended
 * replacements in all the config values.
 */
object ConfigLocator {

  private[this] val log : Logger = Logger[ConfigLocator.type]

  private[this] val sysProps : Config = ConfigFactory.systemProperties()
  private[this] val envProps : Config = ConfigFactory.systemEnvironment()

  private[this] def readConfigFile(f : File, fallback : Config) : List[(String, ConfigValue)] = {
    val cfg : Config = ConfigFactory.parseFile(f)
      .withFallback(fallback)
      .withFallback(sysProps)
      .withFallback(envProps)
      .resolve()

    cfg.entrySet().asScala.map(k => (k.getKey(), k.getValue)).toList
  }

  private[this] def evaluatedConfig(f : File, fallback : Config, ctContext : ContainerContext) : Try[Config] = Try {

    if (f.exists && f.isFile && f.canRead) {

      val rawValues : List[(String, ConfigValue)]= readConfigFile(f, fallback)

      val cfgObj : ConfigObject = rawValues.foldLeft(ConfigFactory.empty().root()){
        case (current, (key, value)) =>
          val replace : ConfigValue = value match {
            case s if s.valueType() == ConfigValueType.STRING =>
              val rawValue : String = s.unwrapped().toString()
              ConfigValueFactory.fromAnyRef(rawValue)
            case v => v
          }

          current.withValue(key, replace)
      }

      cfgObj.toConfig()
    } else {
      ConfigFactory.empty()
    }
  }

  /**
   * Read a configuration file from a given directory.
   */
  def config(cfgDir : String, fileName : String, fallback: Config, ctContext: ContainerContext) : Try[Config] = {
    val file = new File(cfgDir, fileName)
    log.debug(s"Retrieving config from [${file.getAbsolutePath()}]")
    evaluatedConfig(file, fallback, ctContext)
  }

  def safeConfig(cfgDir : String, fileName: String, fallback: Config, ctContext : ContainerContext) : Config =
    config(cfgDir, fileName, fallback, ctContext) match {
      case Failure(e) =>
        log.warn(s"Error reading [$fileName] : [${e.getMessage()}], using empty config")
        ConfigFactory.empty()
      case Success(cfg) => cfg
    }
}
