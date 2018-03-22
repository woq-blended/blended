package blended.container.context.impl.internal

import java.io.File
import java.nio.file.Files

import blended.container.context.api.{ContainerContext, ContainerIdentifierService}
import blended.updater.config.RuntimeConfig
import com.typesafe.config.{ConfigFactory, ConfigParseOptions}

import scala.collection.JavaConverters._
import scala.util.Try

class ContainerIdentifierServiceImpl(override val containerContext: ContainerContext) extends ContainerIdentifierService {

  private[this] val bundleName = classOf[ContainerIdentifierService].getPackage.getName

  private[this] val log = org.log4s.getLogger

  override val uuid : String = {
    val idFile = new File(System.getProperty("blended.home") + "/etc", s"$bundleName.id")
    val lines = Files.readAllLines(idFile.toPath)
    if (!lines.isEmpty) {
      log.info(s"Using Container ID [${lines.get(0)}]")
      lines.get(0)
    } else {
      throw new Exception("Unable to determine Container Id")
    }
  }

  override val properties : Map[String,String] = {

    val mandatoryPropNames : Seq[String] = System.getProperty(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS, "").split(",").toSeq

    val cfgFile = new File(containerContext.getProfileConfigDirectory(), s"$bundleName.conf")
    val cfg = ConfigFactory.parseFile(cfgFile, ConfigParseOptions.defaults().setAllowMissing(false))

    val unresolved : Map[String, String] = cfg.entrySet().asScala.map { entry =>
      (entry.getKey, cfg.getString(entry.getKey)) }.toMap

    val missingPropNames = mandatoryPropNames.filter(p => unresolved.get(p).isEmpty)

    if (!missingPropNames.isEmpty) {
      val msg = s"The configuration file [$bundleName.conf] is missing entries for the properties ${missingPropNames.mkString("[", ",", "]")}"
      throw new RuntimeException(msg)
    }

    val resolve : Map[String, Try[String]] = unresolved.map{ case (k,v) => (k, resolvePropertyString(v)) }

    val resolveErrors = resolve.filter(_._2.isFailure)

    if (!resolveErrors.isEmpty) {
      val msg = "Error resolving container properties : " + resolveErrors.mkString("[", ",", "]")
      throw new RuntimeException(msg)
    }

    resolve.map{ case (k: String, v: Try[String]) => k -> v.get }
  }
}