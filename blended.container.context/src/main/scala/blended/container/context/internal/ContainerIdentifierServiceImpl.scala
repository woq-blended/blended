package blended.container.context.internal

import java.io.File
import java.nio.file.Files

import blended.container.context.{ContainerContext, ContainerIdentifierService}
import blended.launcher.Launcher
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try

class ContainerIdentifierServiceImpl(override val containerContext: ContainerContext) extends ContainerIdentifierService {

  private[this] val bundleName = classOf[ContainerIdentifierService].getPackage.getName

  private[this] val log = LoggerFactory.getLogger(classOf[ContainerIdentifierServiceImpl])

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

    val mandatoryPropNames : Seq[String] = System.getProperty(Launcher.BLENDED_MANDATORY_PROPS, "").split(",").toSeq

    val cfgFile = new File(containerContext.getContainerConfigDirectory(), s"$bundleName.conf")
    val ctxtConfig = ConfigFactory.parseFile(cfgFile)
    val cfg = containerContext.getContainerConfig().withValue(bundleName, ctxtConfig.root().get()).getConfig(bundleName)

    val unresolved : Map[String, String] = cfg.entrySet().asScala.map { entry =>
      (entry.getKey, cfg.getString(entry.getKey)) }.toMap

    val missingPropNames = mandatoryPropNames.filter(p => unresolved.get(p).isEmpty)

    if (!missingPropNames.isEmpty) {
      val msg = s"The configuration file [$bundleName.conf] is missing entries for the properties ${missingPropNames.mkString("[", ",", "]")}"
      throw new RuntimeException(msg)
    }

    val resolve : Map[String, Try[String]] = unresolved.map{ case (k,v) => (k, Try(resolvePropertyString(v))) }

    val resolveErrors = resolve.filter(_._2.isFailure)

    if (!resolveErrors.isEmpty) {
      val msg = "Error resolving container properties : " + resolveErrors.mkString("[", ",", "]")
      throw new RuntimeException(msg)
    }

    resolve.map{ case (k: String, v: Try[String]) => k -> v.get }
  }
}