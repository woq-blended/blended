package blended.container.context.impl.internal

import java.io.File
import java.nio.file.Files

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

import blended.container.context.api.ContainerContext
import blended.security.crypto.{BlendedCryptoSupport, ContainerCryptoSupport}
import blended.updater.config.Profile
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory}

object AbstractContainerContextImpl {
  val PROP_BLENDED_HOME = "blended.home"
  val CONFIG_DIR = "etc"
  val SECRET_FILE_PROP: String = "blended.security.secret"
}

abstract class AbstractContainerContextImpl extends ContainerContext {

  private[this] lazy val log: Logger = Logger(getClass().getName())

  def initialize(): Unit = {
    // then inject the context into the property resolver
    resolver.setCtCtxt(this)
  }

  private lazy val resolver: ContainerPropertyResolver = {
    // make sure all properties are resolved correctly
    new ContainerPropertyResolver(
      ctUuid = uuid,
      properties = properties,
      cryptoSupport = cryptoSupport
    )
  }

  /**
   * Access to a blended resolver for config values
   */
  override def resolveString(s: String, additionalProps: Map[String, Any]): Try[AnyRef] = {
    resolver.resolve(s, additionalProps)
  }

  /**
   * Provide access to encryption and decryption facilities, optionally secured with a secret file.
   */
  override val cryptoSupport: ContainerCryptoSupport = {
    import AbstractContainerContextImpl._

    val cipherSecretFile: String = System.getProperty(SECRET_FILE_PROP, "secret")

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

    val cfg: Config = ConfigLocator.safeConfig(
      profileConfigDirectory,
      s"$id.conf",
      containerConfig,
      this
    )

    val result: Config = if (cfg.isEmpty()) {
      if (containerConfig.hasPath(id)) {
        log.debug(s"Resolving config for [$id] from application config")
        containerConfig.getConfig(id)
      } else {
        ConfigFactory.empty()
      }
    } else {
      cfg
    }

    result
  }

  @BeanProperty
  override lazy val uuid: String = {
    val idFile = new File(containerConfigDirectory, s"blended.container.context.id")
    val lines = Files.readAllLines(idFile.toPath)
    if (!lines.isEmpty) {
      log.info(s"Using Container ID [${lines.get(0)}]")
      lines.get(0)
    } else {
      throw new Exception("Unable to determine Container Id")
    }
  }

  override lazy val properties: Map[String, String] = {

    val propResolver: ContainerPropertyResolver = new ContainerPropertyResolver(
      ctUuid = uuid,
      properties = Map.empty,
      cryptoSupport = cryptoSupport
    )

    val cfg: Config = {
      val f: File = new File(profileConfigDirectory, "blended.container.context.conf")
      if (f.exists()) {
        ConfigLocator.readConfigFile(
          new File(profileConfigDirectory, "blended.container.context.conf"),
          ConfigFactory.empty())
      } else {
        ConfigFactory.empty()
      }
    }

    val mandatoryPropNames: Seq[String] = Option(System.getProperty(Profile.Properties.PROFILE_PROPERTY_KEYS)) match {
      case Some(s) => if (s.trim().isEmpty) Seq.empty else s.trim().split(",").toSeq
      case None    => Seq.empty
    }

    val props: Map[String, String] = cfg
      .entrySet()
      .asScala
      .map { entry =>
        (entry.getKey, cfg.getString(entry.getKey))
      }
      .toMap
      .filter { case (k, _) => mandatoryPropNames.contains(k) }

    val missingPropNames = mandatoryPropNames.filter(p => props.get(p).isEmpty)

    if (missingPropNames.nonEmpty) {
      val msg =
        s"The configuration file [blended.container.context.conf] is missing entries for the properties ${missingPropNames
          .mkString("[", ",", "]")}"
      throw new RuntimeException(msg)
    }

    props.map {
      case (k, v) =>
        propResolver.resolve(v) match {
          case Failure(t) => throw t
          case Success(r) =>
            k -> r.toString()
        }
    }
  }

  override def toString: String =
    s"${getClass().getSimpleName()}(containerDir = $containerDirectory, containerConfigDirectory = $containerConfigDirectory," +
      s"profileDirectory = $profileDirectory, profileConfigDirectory = $profileConfigDirectory" +
      s"containerLogDirectory = $containerLogDirectory, hostName = $containerHostname, uuid = $uuid, properties = (${properties
        .mkString(",")}" +
      s", config size = ${containerConfig.entrySet().size()} keys)"
}
