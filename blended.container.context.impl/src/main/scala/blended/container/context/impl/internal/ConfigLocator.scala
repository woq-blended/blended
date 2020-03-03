package blended.container.context.impl.internal

import java.io.File

import blended.container.context.api.ContainerContext
import blended.util.logging.Logger
import com.typesafe.config._
import blended.util.RichTry._

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

  private[this] def readConfigFile(f : File, fallback : Config) : Config = ConfigFactory.parseFile(f)
    .withFallback(fallback)
    .withFallback(sysProps)
    .withFallback(envProps)
    .resolve()

  private[this] def evaluatedConfig(f : File, fallback : Config, ctContext : ContainerContext) : Try[Config] = Try {

    if (f.exists && f.isFile && f.canRead) {

      val replaced = readConfigFile(f, fallback).entrySet().asScala.map{ e =>
        val v : AnyRef = e.getValue() match {
          case s if s.valueType() == ConfigValueType.STRING => ctContext.resolveString(s.unwrapped().toString()).map(_.toString()).unwrap
          case o => o.unwrapped()
        }
        e.getKey() -> ConfigValueFactory.fromAnyRef(v)
      }.toMap.asJava

      ConfigFactory.parseMap(replaced).resolve()
    } else {
      ConfigFactory.empty()
    }
  }

  def safeConfig(cfgDir : String, fileName: String, fallback: Config, ctContext : ContainerContext) : Config = {
    val file = new File(cfgDir, fileName)
    log.debug(s"Retrieving config from [${file.getAbsolutePath()}]")

    evaluatedConfig(file, fallback, ctContext) match {
      case Failure(e) =>
        log.warn(s"Error reading [$fileName] : [${e.getMessage()}], using empty config")
        ConfigFactory.empty()
      case Success(cfg) =>
        log.debug(s"Resolved config from [${file.getAbsolutePath()}]")
        cfg
    }
  }
}
