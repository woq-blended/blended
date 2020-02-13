package blended.container.context.api

import java.io.File

import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory}

object ConfigLocator {

  private[this] val log = Logger[ConfigLocator.type]

  private[this] val sysProps = ConfigFactory.systemProperties()
  private[this] val envProps = ConfigFactory.systemEnvironment()

  /**
   * Read a configuration file from a given directory.
   */
  def config(cfgDir : File, fileName : String, fallback: Config) : Config = {
    val file = new File(cfgDir, fileName)
    log.debug(s"Retrieving config from [${file.getAbsolutePath()}]")

    if (file.exists && file.isFile && file.canRead) {
      ConfigFactory.parseFile(file)
        .withFallback(fallback)
        .withFallback(sysProps)
        .withFallback(envProps)
        .resolve()
    } else {
      ConfigFactory.empty()
    }
  }
}
