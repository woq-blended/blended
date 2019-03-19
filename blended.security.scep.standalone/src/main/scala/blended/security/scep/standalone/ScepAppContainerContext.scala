package blended.security.scep.standalone

import java.io.File

import blended.container.context.api.ContainerContext
import blended.security.crypto.{BlendedCryptoSupport, ContainerCryptoSupport}
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

class ScepAppContainerContext(baseDir: String) extends ContainerContext {

  private val SECRET_FILE_PATH  : String = "blended.security.secretFile"

  override def getContainerDirectory(): String = baseDir

  override def getContainerConfigDirectory(): String = getContainerDirectory() + "/container"

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

    BlendedCryptoSupport.initCryptoSupport(
      new File(getContainerConfigDirectory(), cipherSecretFile).getAbsolutePath()
    )
  }

  override def getContainerCryptoSupport(): ContainerCryptoSupport = cryptoSupport

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
