package blended.container.context.impl.internal

import java.io.File
import java.util.Properties

import com.typesafe.config.impl.Parseable
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigParseOptions}

class MockContainerContext(baseDir : String) extends AbstractContainerContextImpl {

  private lazy val SECRET_FILE_PATH : String = "blended.security.secretFile"

  override lazy val containerDirectory : String = baseDir

  override lazy val containerConfigDirectory : String = containerDirectory + "/etc"

  override lazy val containerLogDirectory : String = baseDir

  override lazy val profileDirectory : String = containerDirectory

  override lazy val profileConfigDirectory : String = containerConfigDirectory

  override lazy val containerHostname : String = "localhost"

  private def getSystemProperties() : Properties = {
    // Avoid ConcurrentModificationException due to parallel setting of system properties by copying properties
    val systemProperties = System.getProperties()
    val systemPropertiesCopy = new Properties()
    systemPropertiesCopy.putAll(systemProperties)
    systemPropertiesCopy
  }

  private def loadSystemProperties() : ConfigObject = {
    Parseable
      .newProperties(
        getSystemProperties(),
        ConfigParseOptions.defaults().setOriginDescription("system properties")
      )
      .parse()
  }

  override lazy val containerConfig : Config = {
    val sysProps = loadSystemProperties()
    val envProps = ConfigFactory.systemEnvironment()

    ConfigFactory
      .parseFile(
        new File(profileConfigDirectory, "application.conf"),
        ConfigParseOptions.defaults().setAllowMissing(false)
      )
      .withFallback(sysProps)
      .withFallback(envProps)
      .resolve()
  }


}
