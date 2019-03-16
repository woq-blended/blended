package blended.testsupport.pojosr

import java.io.File
import java.util.Properties

import blended.container.context.api.{ContainerContext, ContainerCryptoSupport}
import blended.container.context.impl.internal.ContainerCryptoSupportImpl
import com.typesafe.config.impl.Parseable
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigParseOptions}

class MockContainerContext(baseDir: String) extends ContainerContext {

  private val SECRET_FILE_PATH  : String = "blended.security.secretFile"

  override def getContainerDirectory(): String = baseDir

  override def getContainerConfigDirectory(): String = getContainerDirectory() + "/etc"

  override def getContainerLogDirectory(): String = baseDir

  override def getProfileDirectory(): String = getContainerDirectory()

  override def getProfileConfigDirectory(): String = getContainerConfigDirectory()

  override def getContainerHostname(): String = "localhost"

  private lazy val cryptoSupport : ContainerCryptoSupport = {
    val ctConfig : Config = getContainerConfig()

    val cipherSecretFile : String = if (ctConfig.hasPath(SECRET_FILE_PATH)) {
      ctConfig.getString(SECRET_FILE_PATH)
    } else {
      "secret"
    }

    ContainerCryptoSupportImpl.initCryptoSupport(
      new File(getContainerConfigDirectory(), cipherSecretFile).getAbsolutePath()
    )
  }

  override def getContainerCryptoSupport(): ContainerCryptoSupport = cryptoSupport

  private def getSystemProperties(): Properties = {
    // Avoid ConcurrentModificationException due to parallel setting of system properties by copying properties
    val systemProperties     = System.getProperties()
    val systemPropertiesCopy = new Properties()
    systemPropertiesCopy.putAll(systemProperties)
    systemPropertiesCopy
  }

  private def loadSystemProperties(): ConfigObject = {
    Parseable
      .newProperties(
        getSystemProperties(),
        ConfigParseOptions.defaults().setOriginDescription("system properties")
      )
      .parse()
  }

  override def getContainerConfig(): Config = {
    val sysProps = loadSystemProperties()
    val envProps = ConfigFactory.systemEnvironment()

    ConfigFactory
      .parseFile(
        new File(getProfileConfigDirectory(), "application.conf"),
        ConfigParseOptions.defaults().setAllowMissing(false)
      )
      .withFallback(sysProps)
      .withFallback(envProps)
      .resolve()
  }
}
