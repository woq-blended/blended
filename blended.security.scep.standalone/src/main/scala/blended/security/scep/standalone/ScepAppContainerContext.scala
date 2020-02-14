package blended.security.scep.standalone

import java.io.File
import java.util.Properties

import blended.container.context.api.ContainerContext
import blended.security.crypto.{BlendedCryptoSupport, ContainerCryptoSupport}
import blended.util.logging.Logger
import com.typesafe.config.impl.Parseable
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

class ScepAppContainerContext(baseDir : String) extends ContainerContext {

  private[this] val log = Logger[this.type]
  log.debug(s"Created ${getClass().getName()} with baseDir [${baseDir}]")

  private val SECRET_FILE_PATH : String = "blended.security.secretFile"

  override def containerDirectory() : String = baseDir

  override def containerConfigDirectory() : String = containerDirectory() + "/etc"

  override def containerLogDirectory() : String = baseDir

  override def profileDirectory() : String = containerDirectory()

  override def profileConfigDirectory() : String = containerConfigDirectory()

  override def containerHostname() : String = "localhost"

  private lazy val cryptoSupport : ContainerCryptoSupport = {
    val ctConfig : Config = containerConfig()

    val cipherSecretFile : String = if (ctConfig.hasPath(SECRET_FILE_PATH)) {
      ctConfig.getString(SECRET_FILE_PATH)
    } else {
      "secret"
    }

    BlendedCryptoSupport.initCryptoSupport(
      new File(containerConfigDirectory(), cipherSecretFile).getAbsolutePath()
    )
  }

  override def cryptoSupport() : ContainerCryptoSupport = cryptoSupport

  override def containerConfig() : Config = {
    val sys = new Properties()
    sys.putAll(System.getProperties())
    val sysProps = Parseable.newProperties(sys, ConfigParseOptions.defaults().setOriginDescription("system properties")).parse()
    val envProps = ConfigFactory.systemEnvironment()

    ConfigFactory.parseFile(
      new File(profileConfigDirectory(), "application.conf"),
      ConfigParseOptions.defaults().setAllowMissing(false)
    ).
      withFallback(sysProps).
      withFallback(envProps).
      withFallback(ConfigFactory.parseResources(getClass().getClassLoader(), "application-defaults.conf")).
      resolve()
  }
}
