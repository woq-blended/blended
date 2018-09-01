package blended.domino

import java.io.File

import blended.container.context.api.ContainerContext
import blended.util.logging.Logger
import com.typesafe.config.{ Config, ConfigFactory }

class ConfigLocator(ctContext: ContainerContext) {

  private[this] val log = Logger[ConfigLocator]

  private[this] val sysProps = ConfigFactory.systemProperties()
  private[this] val envProps = ConfigFactory.systemEnvironment()

  private[this] def config(fileName : String) : Config = {
    val file = new File(ctContext.getProfileConfigDirectory(), fileName)
    log.debug(s"Retrieving config from [${file.getAbsolutePath()}]")

    if (file.exists && file.isFile && file.canRead)
      ConfigFactory.parseFile(file).withFallback(sysProps).withFallback(envProps).resolve()
    else
      ConfigFactory.empty()
  }

  /**
    * Retrieve a configuartion by it's id.
    *
    * If a config file <code>id.conf</code> exists in the configuration directory, the result
    * is the content of that file read in as a Config object.
    *
    * If no such file exists, the global container configuration is checked whether it has a
    * sub section <code>id</code>. If that is the case, the return value is that subsection as
    * a <code>Config</code> object.
    *
    * If no config can be found in either case, an empty <code>Config</code> object will be returned.
    *
    * @param id The config id to search for
    * @return The <code>Config</code> object as specified above
    */
  def getConfig(id: String): Config = config(s"$id.conf") match {
    case empty if empty.isEmpty =>
      val cfg = ctContext.getContainerConfig()
      if (cfg.hasPath(id)) cfg.getConfig(id) else ConfigFactory.empty()

    case cfg => cfg
  }
}
