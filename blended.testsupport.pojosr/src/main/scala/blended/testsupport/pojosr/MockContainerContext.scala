package blended.testsupport.pojosr

import java.io.File
import java.nio.file.Files
import java.util.Properties

import blended.container.context.impl.internal.AbstractContainerContextImpl
import com.typesafe.config.impl.Parseable
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigParseOptions}

import scala.beans.BeanProperty

class MockContainerContext(baseDir : String, ctid : String) extends AbstractContainerContextImpl {

  private val SECRET_FILE_PATH : String = "blended.security.secretFile"

  @BeanProperty
  override lazy val containerDirectory : String = baseDir

  @BeanProperty
  override lazy val containerConfigDirectory : String = containerDirectory + "/etc"

  @BeanProperty
  override lazy val containerLogDirectory : String = baseDir

  @BeanProperty
  override lazy val profileDirectory : String = containerDirectory

  @BeanProperty
  override lazy val profileConfigDirectory : String = containerConfigDirectory

  @BeanProperty
  override lazy val containerHostname : String = "localhost"

  @BeanProperty
  override lazy val uuid : String = ctid

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
