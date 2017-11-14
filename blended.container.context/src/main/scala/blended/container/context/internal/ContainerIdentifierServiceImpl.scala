package blended.container.context.internal

import java.io.File
import java.nio.file.Files

import blended.container.context.{ContainerContext, ContainerIdentifierService}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._

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
    val cfgFile = new File(containerContext.getContainerConfigDirectory(), s"$bundleName.conf")
    val ctxtConfig = ConfigFactory.parseFile(cfgFile)
    val cfg = containerContext.getContainerConfig().withValue(bundleName, ctxtConfig.root().get()).getConfig(bundleName)

    val unresolved = cfg.entrySet().asScala.map { case entry =>
      (entry.getKey, cfg.getString(entry.getKey)) }.toMap

    unresolved.map{ case (k,v) => (k, resolvePropertyString(v)) }
  }
}