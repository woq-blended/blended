package blended.container.context.impl.internal

import java.io.File
import java.nio.file.Files

import blended.container.context.api.{ContainerContext, ContainerIdentifierService}
import blended.updater.config.RuntimeConfig
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory}

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.util.Try

class ContainerIdentifierServiceImpl(
  ctContext : ContainerContext
) extends ContainerIdentifierService {

  private[this] val log = Logger[ContainerIdentifierServiceImpl]
  private[this] val ctConfigDir : String = ctContext.containerConfigDirectory
  private[this] val profileConfigDir : String = ctContext.profileConfigDirectory

  @BeanProperty
  override lazy val uuid : String = {
    val idFile = new File(ctConfigDir, s"blended.container.context.id")
    val lines = Files.readAllLines(idFile.toPath)
    if (!lines.isEmpty) {
      log.info(s"Using Container ID [${lines.get(0)}]")
      lines.get(0)
    } else {
      throw new Exception("Unable to determine Container Id")
    }
  }

  @BeanProperty
  override val properties : Map[String, String] = {

    val mandatoryPropNames : Seq[String] = Option(System.getProperty(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS)) match {
      case Some(s) => if (s.trim().isEmpty) Seq.empty else s.trim().split(",").toSeq
      case None    => Seq.empty
    }

    val cfg : Config = ConfigLocator.safeConfig(profileConfigDir, "blended.container.context.conf", ConfigFactory.empty(), ctContext)

    val unresolved : Map[String, String] = cfg.entrySet().asScala.map { entry =>
      (entry.getKey, cfg.getString(entry.getKey))
    }.toMap

    val missingPropNames = mandatoryPropNames.filter(p => unresolved.get(p).isEmpty)

    if (missingPropNames.nonEmpty) {
      val msg = s"The configuration file [blended.container.context.conf] is missing entries for the properties ${missingPropNames.mkString("[", ",", "]")}"
      throw new RuntimeException(msg)
    }

    val resolve : Map[String, Try[String]] = unresolved.map { case (k, v) => (k, ctContext.resolveString(v).map(_.toString())) }

    val resolveErrors = resolve.filter(_._2.isFailure)

    if (resolveErrors.nonEmpty) {
      val msg = "Error resolving container properties : " + resolveErrors.mkString("[", ",", "]")
      throw new RuntimeException(msg)
    }

    resolve.map { case (k : String, v : Try[String]) => k -> v.get }
  }
}
