package blended.container.context.impl.internal

import java.io.File
import java.nio.file.Files

import scala.collection.JavaConverters._
import scala.util.{Success, Try}
import blended.container.context.api.{ContainerContext, ContainerIdentifierService}
import blended.updater.config.RuntimeConfig
import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

import scala.beans.BeanProperty

class ContainerIdentifierServiceImpl(
  @BeanProperty
  override val containerContext: ContainerContext
) extends ContainerIdentifierService {

  private[this] val log = Logger[ContainerIdentifierServiceImpl]

  @BeanProperty
  override lazy val uuid : String = {
    val idFile = new File(System.getProperty("blended.home") + "/etc", s"blended.container.context.id")
    val lines = Files.readAllLines(idFile.toPath)
    if (!lines.isEmpty) {
      log.info(s"Using Container ID [${lines.get(0)}]")
      lines.get(0)
    } else {
      throw new Exception("Unable to determine Container Id")
    }
  }

  @BeanProperty
  override val properties : Map[String,String] = {

    val mandatoryPropNames : Seq[String] = Option(System.getProperty(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS)) match {
      case Some(s) => if (s.trim().isEmpty) Seq.empty else s.trim().split(",").toSeq
      case None => Seq.empty
    }

    val cfgFile = new File(containerContext.getProfileConfigDirectory(), "blended.container.context.conf")
    val cfg : Config = Try {
      ConfigFactory.parseFile(cfgFile, ConfigParseOptions.defaults().setAllowMissing(false))
    }.recoverWith {
      case _ : Throwable => Success(ConfigFactory.empty())
    }.get

    val unresolved : Map[String, String] = cfg.entrySet().asScala.map { entry =>
      (entry.getKey, cfg.getString(entry.getKey)) }.toMap

    val missingPropNames = mandatoryPropNames.filter(p => unresolved.get(p).isEmpty)

    if (!missingPropNames.isEmpty) {
      val msg = s"The configuration file [blended.container.context.conf] is missing entries for the properties ${missingPropNames.mkString("[", ",", "]")}"
      throw new RuntimeException(msg)
    }

    val resolve : Map[String, Try[String]] = unresolved.map{ case (k,v) => (k, resolvePropertyString(v).map(_.toString())) }

    val resolveErrors = resolve.filter(_._2.isFailure)

    if (!resolveErrors.isEmpty) {
      val msg = "Error resolving container properties : " + resolveErrors.mkString("[", ",", "]")
      throw new RuntimeException(msg)
    }

    resolve.map{ case (k: String, v: Try[String]) => k -> v.get }
  }
}
