package blended.testsupport.pojosr

import java.io.File
import java.util.Properties

import blended.container.context.api.{ContainerContext, ContainerIdentifierService}
import blended.security.crypto.{BlendedCryptoSupport, ContainerCryptoSupport}
import com.typesafe.config.impl.Parseable
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigParseOptions}

class MockContainerContext(baseDir : String) extends ContainerContext {

  private val SECRET_FILE_PATH : String = "blended.security.secretFile"

  override val containerDirectory : String = baseDir

  override val containerConfigDirectory : String = containerDirectory + "/etc"

  override val containerLogDirectory : String = baseDir

  override val profileDirectory : String = containerDirectory

  override val profileConfigDirectory : String = containerConfigDirectory(

  override val containerHostname : String = "localhost"

  private lazy val cryptoSupport : ContainerCryptoSupport = {
    val ctConfig : Config = containerConfig

    val cipherSecretFile : String = if (ctConfig.hasPath(SECRET_FILE_PATH)) {
      ctConfig.getString(SECRET_FILE_PATH)
    } else {
      "secret"
    }

    BlendedCryptoSupport.initCryptoSupport(
      new File(containerConfigDirectory, cipherSecretFile).getAbsolutePath()
    )
  }

  override val containerCryptoSupport : ContainerCryptoSupport = cryptoSupport

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

  override val containerConfig : Config = {
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
