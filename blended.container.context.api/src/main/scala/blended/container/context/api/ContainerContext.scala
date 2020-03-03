package blended.container.context.api

import java.util.concurrent.atomic.AtomicLong

import blended.security.crypto.ContainerCryptoSupport
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory}

import scala.beans.BeanProperty
import scala.util.{Failure, Success, Try}

class PropertyResolverException(msg : String) extends Exception(msg)

object ContainerContext {

  val transactionCounter = new AtomicLong(0)

  def nextTransactionCounter : Long = {
    if (transactionCounter.get() == Long.MaxValue) {
      transactionCounter.set(0L)
    }

    transactionCounter.incrementAndGet()
  }
}

trait ContainerContext {

  private[this] val log : Logger = Logger[ContainerContext]
  /**
   * The home directory of the container, usually defined by the system property <code>blended.home</code>
   */
  @BeanProperty
  val containerDirectory : String


  @BeanProperty
  val containerConfigDirectory : String

  /**
   * The target directory for the container log files
   */
  @BeanProperty
  val containerLogDirectory : String

  /**
   * The base directory for the current container profile
   */
  @BeanProperty
  val profileDirectory : String

  /**
   * The config directory for all profile specific configuration files. Usually this is
   * <code>getProfileDirectory()/etc</code>. This is the main config directory.
   */
  @BeanProperty
  val profileConfigDirectory : String

  /**
   * The hostname of the current container as defined by the netowrk layer.
   */
  @BeanProperty
  val containerHostname : String

  /**
   * Provide access to encryption and decryption facilities, optionally secured with a secret file.
   */
  @BeanProperty
  val cryptoSupport : ContainerCryptoSupport

  /**
   * The application.conf, optionally modified with an overlay.
    */
  @BeanProperty
  val containerConfig : Config

  /**
   * Read a config with a given id from the profile config directory and apply all blended
   * replacements in the result.
   * @param id The id to retrieve the config for. This is usually the bundle symbolic name.
   */
  def getConfig(id : String) : Config

  /**
   * Access to the Container Identifier Service
   */
  @BeanProperty
  val identifierService : ContainerIdentifierService

  /**
   * Access to a blended resolver for config values
   */
  def resolveString(s : String, additionalProps : Map[String, Any] = Map.empty) : Try[AnyRef]

  /**
   * Provide access to the next transaction number
   */
  def getNextTransactionCounter() : Long = ContainerContext.nextTransactionCounter
}
