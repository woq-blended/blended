package blended.security.scep.standalone

import java.io.File

import blended.container.context.api.ContainerContext
import com.typesafe.config.{ Config, ConfigFactory, ConfigParseOptions }

class ScepAppContainerContext(baseDir: String) extends ContainerContext {

  override def getContainerDirectory(): String = baseDir

  override def getContainerConfigDirectory(): String = getContainerDirectory() + "/container"

  override def getContainerLogDirectory(): String = baseDir

  override def getProfileDirectory(): String = getContainerDirectory()

  override def getProfileConfigDirectory(): String = getContainerConfigDirectory()

  override def getContainerHostname(): String = "localhost"

  override def getContainerConfig(): Config = {
    val sysProps = ConfigFactory.systemProperties()
    val envProps = ConfigFactory.systemEnvironment()

    ConfigFactory.parseFile(
      new File(getProfileConfigDirectory(), "application.conf"),
      ConfigParseOptions.defaults().setAllowMissing(false)
    ).
      withFallback(sysProps).
      withFallback(envProps).
      withFallback(ConfigFactory.parseResources(getClass().getClassLoader(), "application-defaults.conf")).
      resolve()
  }
}
