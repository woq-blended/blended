package blended.updater.config

import com.typesafe.config.Config
import _root_.scala.collection.immutable
import java.io.File
import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.Left
import scala.util.Right
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import scala.util.Failure
import org.slf4j.LoggerFactory
import scala.util.Success

final case class OverlayRef(name: String, version: String) extends Ordered[OverlayRef] {
  override def toString(): String = name + "-" + version
  override def compare(other: OverlayRef): Int = toString().compare(other.toString())
}

final case class OverlayConfig(
    name: String,
    version: String,
    generatedConfigs: immutable.Seq[GeneratedConfig] = immutable.Seq()) extends Ordered[OverlayConfig] {

  override def compare(other: OverlayConfig): Int = overlayRef.compare(other.overlayRef)

  def overlayRef: OverlayRef = OverlayRef(name, version)

  def validate(): Seq[String] = {
    OverlayConfig.findCollisions(generatedConfigs)
  }

  override def toString(): String = s"${getClass().getSimpleName()}(name=${name},version=${version},generatedConfigs=${generatedConfigs})"

}

final object OverlayConfig extends ((String, String, immutable.Seq[GeneratedConfig]) => OverlayConfig) {

  def findCollisions(generatedConfigs: Seq[GeneratedConfig]): Seq[String] = {
    aggregateGeneratedConfigs(generatedConfigs) match {
      case Left(issues) => issues
      case _ => Nil
    }
  }

  def aggregateGeneratedConfigs(generatedConfigs: Iterable[GeneratedConfig]): Either[Seq[String], Map[String, Map[String, Object]]] = {
    // seen configurations per target file
    var fileToConfig: Map[String, Map[String, Object]] = Map()
    val issues = generatedConfigs.flatMap { gc =>
      val newConfig = gc.config.root().unwrapped().asScala.toMap
      fileToConfig.get(gc.configFile) match {
        case None =>
          // no collision
          fileToConfig += gc.configFile -> newConfig
          Seq()
        case Some(existingConfig) =>
          // check collisions
          val collisions = existingConfig.keySet.intersect(newConfig.keySet)
          fileToConfig += gc.configFile -> (existingConfig ++ newConfig)
          collisions.map(c => s"Double defined config key found: ${c}")
      }
    }
    if (issues.isEmpty) Right(fileToConfig) else Left(issues.toList)
  }

  def read(config: Config): Try[OverlayConfig] = Try {
    OverlayConfig(
      name = config.getString("name"),
      version = config.getString("version"),
      generatedConfigs = if (config.hasPath("configGenerator")) {
        val gens = config.getObject("configGenerator").entrySet().asScala
        gens.map { entry =>
          val fileName = entry.getKey()
          val genConfig = entry.getValue().asInstanceOf[ConfigObject].toConfig()
          GeneratedConfig(configFile = fileName, config = genConfig)
        }.toList
      } else Nil
    )
  }

  def toConfig(overlayConfig: OverlayConfig): Config = {
    val config = Map(
      "name" -> overlayConfig.name,
      "version" -> overlayConfig.version,
      "configGenerator" -> overlayConfig.generatedConfigs.map { genConfig =>
        genConfig.configFile -> genConfig.config.root().unwrapped()
      }.toMap.asJava
    ).asJava
    ConfigFactory.parseMap(config)
  }

}

/**
 * A materialized set of overlays.
 */
final case class LocalOverlays(overlays: Set[OverlayConfig], profileDir: File) {

  def overlayRefs: Set[OverlayRef] = overlays.map(_.overlayRef)

  def validate(): Seq[String] = {
    val nameIssues = overlays.groupBy(_.name).collect {
      case (name, configs) if configs.size > 1 => s"More than one overlay with name '${name}' detected"
    }.toList
    val generatorIssues = OverlayConfig.findCollisions(overlays.toList.flatMap(_.generatedConfigs))
    nameIssues ++ generatorIssues
  }

  /**
   * The location of the final applied set of overlays.
   */
  def materializedDir: File = LocalOverlays.materializedDir(overlays.map(_.overlayRef), profileDir)

  def materialize(): Try[immutable.Seq[File]] = Try {
    val dir = materializedDir
    OverlayConfig.aggregateGeneratedConfigs(overlays.flatMap(_.generatedConfigs)) match {
      case Left(issues) =>
        sys.error("Cannot materialize invalid or inconsistent overlays. Issues: " + issues.mkString(";"))
      case Right(configByFile) =>
        configByFile.map {
          case (fileName, config) =>
            val file = new File(dir, fileName)
            file.getParentFile().mkdirs()
            val configFileContent = ConfigFactory.parseMap(config.asJava)
            ConfigWriter.write(configFileContent, file, None)
            file
        }.toList
    }
  }

  /**
   * The files that would be generated
   */
  def materializedFiles(): Try[immutable.Seq[File]] = Try {
    val dir = materializedDir
    OverlayConfig.aggregateGeneratedConfigs(overlays.flatMap(_.generatedConfigs)) match {
      case Left(issues) =>
        sys.error("Cannot materialize invalid or inconsistent overlays. Issues: " + issues.mkString(";"))
      case Right(configByFile) =>
        configByFile.map {
          case (fileName, config) => new File(dir, fileName)
        }.toList
    }
  }

  def isMaterialized(): Boolean = {
    materializedFiles() match {
      case Success(files) => files.forall { f => f.exists() && f.isFile() }
      case _ => false
    }
  }

}

final object LocalOverlays {

  lazy val log = LoggerFactory.getLogger(classOf[LocalOverlays].getName())

  def materializedDir(overlays: Iterable[OverlayRef], profileDir: File): File = {
    if (overlays.isEmpty) {
      profileDir
    } else {
      val overlayParts = overlays.toList.map(o => s"${o.name}-${o.version}").distinct.sorted
      new File(profileDir, overlayParts.mkString("/"))
    }
  }

  def preferredConfigFile(overlays: Iterable[OverlayRef], profileDir: File): File = {
    val overlayPart =
      if (overlays.isEmpty) "base"
      else overlays.toList.map(o => s"${o.name}-${o.version}").distinct.sorted.mkString("_")

    new File(profileDir, s"overlays/${overlayPart}.conf")
  }

  def read(config: Config, profileDir: File): Try[LocalOverlays] = Try {
    LocalOverlays(
      profileDir = profileDir,
      overlays = config.getObjectList("overlays").asScala.map { c =>
        OverlayConfig.read(c.toConfig()).get
      }.toSet
    )
  }

  def toConfig(localOverlays: LocalOverlays): Config = {
    val config = (Map(
      "overlays" -> localOverlays.overlays.toList.sorted.map(OverlayConfig.toConfig).map(_.root().unwrapped()).asJava
    ).asJava)
    ConfigFactory.parseMap(config)
  }

  def findLocalOverlays(profileDir: File): List[LocalOverlays] = {
    val overlaysDir = new File(profileDir, "overlays")
    val candidates = Option(overlaysDir.listFiles()).getOrElse(Array()).filter(f => f.isFile() && f.getName().endsWith(".conf"))
    val localOverlays = candidates.toList.flatMap { file =>
      val overlay = Try(ConfigFactory.parseFile(file)).flatMap(c => LocalOverlays.read(c, profileDir))
      overlay match {
        case Success(localOverlays) =>
          List(localOverlays)
        case Failure(e) =>
          log.error("Could not read overlay config file: {}", Array(file, e))
          List()
      }
    }
    localOverlays
  }

}

case class GeneratedConfig(configFile: String, config: Config)