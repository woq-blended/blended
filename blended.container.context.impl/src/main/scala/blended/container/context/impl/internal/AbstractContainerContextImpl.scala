package blended.container.context.impl.internal

import java.io.File

import blended.container.context.api.{ContainerContext, ContainerIdentifierService}
import blended.security.crypto.{BlendedCryptoSupport, ContainerCryptoSupport}
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory}

import scala.beans.BeanProperty
import scala.util.{Failure, Success, Try}

object AbstractContainerContextImpl {
  val PROP_BLENDED_HOME = "blended.home"
  val CONFIG_DIR = "etc"
  val SECRET_FILE_PATH : String = "blended.security.secretFile"
}

abstract class AbstractContainerContextImpl extends ContainerContext {

  private val log : Logger = Logger(getClass().getName())

  private val resolver : ContainerPropertyResolver = new ContainerPropertyResolver(this)

  /**
   * Access to the Container Identifier Service
   */
  @BeanProperty
  override val identifierService: ContainerIdentifierService = new ContainerIdentifierServiceImpl(this)

  /**
   * Access to a blended resolver for config values
   */
  override def resolveString(s: String, additionalProps: Map[String, Any]): Try[AnyRef] = Try {
    resolver.resolve(s, additionalProps)
  }

  /**
   * Provide access to encryption and decryption facilities, optionally secured with a secret file.
   */
  @BeanProperty
  override val cryptoSupport: ContainerCryptoSupport = {
    import AbstractContainerContextImpl._

    val cipherSecretFile : String = if (containerConfig.hasPath(SECRET_FILE_PATH)) {
      containerConfig.getString(SECRET_FILE_PATH)
    } else {
      "secret"
    }

    BlendedCryptoSupport.initCryptoSupport(
      new File(containerConfigDirectory, cipherSecretFile).getAbsolutePath()
    )
  }

  /**
   * Read a config with a given id from the profile config directory and apply all blended
   * replacements in the result.
   *
   * @param id The id to retrieve the config for. This is usually the bundle symbolic name.
   */
  override def getConfig(id: String): Config = {

    ConfigLocator.config(
      containerConfigDirectory, s"$id.conf", containerConfig, this
    ) match {
      case Failure(e) =>
        log.warn(s"Failed to read config for id [$id] : [${e.getMessage()}], using empty config")
        ConfigFactory.empty()
      case Success(empty) if empty.isEmpty =>
        val cfg = containerConfig
        if (cfg.hasPath(id)) cfg.getConfig(id) else ConfigFactory.empty()
      case Success(cfg) => cfg
    }
  }
}
