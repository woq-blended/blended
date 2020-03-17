package blended.container.context.impl.internal

import java.io.{File, FileReader, Reader}

import blended.container.context.api.ContainerContext
import blended.util.logging.Logger
import com.typesafe.config._
import blended.util.RichTry._

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * A helper class to read HOCON config files from the file system and apply blended
 * replacements in all the config values.
 */
object ConfigLocator {

  private[this] val log : Logger = Logger[ConfigLocator.type]

  private[this] val sysProps : Config = ConfigFactory.systemProperties()
  private[this] val envProps : Config = ConfigFactory.systemEnvironment()

  private[internal] def readConfigFile(f : File, fallback : Config) : Config = {

    if (f.exists() && f.isFile() && f.canRead()) {
      ConfigFactory.parseFile(f, ConfigParseOptions.defaults().setAllowMissing(false))
        .withFallback(fallback)
        .withFallback(sysProps)
        .withFallback(envProps)
        .resolve()
    } else {
      ConfigFactory.empty()
    }
  }

  private[internal] def fullKeyset(prefix : String, cfg: Config) : List[String] = {
    val keySet : List[String] = cfg.root().keySet().asScala.toList

    val toAdd : List[List[String]] = keySet.map{ s =>
      if (cfg.getIsNull(s)) {
        List(prefix + s)
      } else {
        try {
          List(prefix + s) ++ fullKeyset(prefix + s + ".", cfg.getConfig(s))
        } catch {
          case NonFatal(_) => List(prefix + s)
        }
      }
    }

    toAdd.flatten
  }

  private[internal] def evaluatedConfig(rawCfg : Config, ctContext : ContainerContext) : Try[Config] = Try {
    val replaced = rawCfg.entrySet().asScala.map{ e =>
      val v : AnyRef = e.getValue() match {
        case s if s.valueType() == ConfigValueType.STRING =>
          val resolved : String = ctContext.resolveString(s.unwrapped().toString()).map(_.toString()).unwrap
          resolved
        case o =>
          o.unwrapped()
      }
      e.getKey() -> ConfigValueFactory.fromAnyRef(v)
    }.toMap.asJava

    ConfigFactory.parseMap(replaced).resolve()
  }

  def safeConfig(cfgDir : String, fileName: String, fallback: Config, ctContext : ContainerContext) : Config = {
    val file = new File(cfgDir, fileName)
    log.debug(s"Retrieving config from [${file.getAbsolutePath()}]")

    evaluatedConfig(readConfigFile(file, fallback), ctContext) match {
      case Failure(e) =>
        log.warn(s"Error reading [${file.getAbsolutePath()}] : [${e.getMessage()}], using empty config")
        ConfigFactory.empty()
      case Success(cfg) =>
        cfg
    }
  }
}
