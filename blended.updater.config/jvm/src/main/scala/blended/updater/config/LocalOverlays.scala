package blended.updater.config

import java.io.File

import blended.util.logging.Logger
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import scala.jdk.CollectionConverters._
import scala.collection.immutable.Map
import scala.util._

/**
 * A materialized set of overlays.
 * The overlays are materialized to the given `profileDir` directory.
 *
 * @param overlays   Alls involved overlay config.
 * @param profileDir The profile directory.
 */
final case class LocalOverlays(overlays : Set[OverlayConfig], profileDir : File) {

  def overlayRefs : Set[OverlayRef] = overlays.map(_.overlayRef).toSet

  /**
   * Validate this set of overlays.
   * Validation checks for collisions of config names and config settings.
   *
   * @return A collection of validation errors, if any.
   *         If this is empty, the validation was successful.
   */
  def validate(): Seq[String] = {
    val nameIssues: Seq[String] = overlays.groupBy(_.name).collect {
      case (name, configs) if configs.size > 1 => s"More than one overlay with name '$name' detected"
    }.toSeq
    var seenPropsAndConfigNames = Set[(String, String)]()
    val propIssues: Seq[String] = overlays.toSeq.flatMap { o =>
      val occ = s"${o.name}-${o.version}"
      o.properties.keySet.toSeq.flatMap { p =>
        seenPropsAndConfigNames.find(_._1 == p) match {
          case Some(first) =>
            val showOcc = List(first._2, occ).sorted.mkString(", ")
            List(s"Duplicate property definitions detected. Property: ${p} Occurences: ${showOcc}")
          case None =>
            seenPropsAndConfigNames += p -> occ
            List()
        }
      }
    }
    val generatorIssues = OverlayConfigCompanion.findCollisions(overlays.toList.flatMap(_.generatedConfigs))
    nameIssues ++ propIssues ++ generatorIssues
  }

  /**
   * The location of the final applied set of overlays.
   */
  def materializedDir : File = LocalOverlays.materializedDir(overlays.map(_.overlayRef), profileDir)

  /**
   * Materializes the given local overlays.
   *
   * @return The `Success` with the materialized config files or `Failure` in case of any unrecoverable error.
   */
  def materialize(): Try[Seq[File]] = Try {
    val dir = materializedDir
    OverlayConfigCompanion.aggregateGeneratedConfigs2(overlays.flatMap(_.generatedConfigs)) match {
      case Left(issues) =>
        sys.error("Cannot materialize invalid or inconsistent overlays. Issues: " + issues.mkString(";"))
      case Right(configByFile) =>
        configByFile.map {
          case (fileName, config) =>
            val file = new File(dir, fileName)
            file.getParentFile().mkdirs()
            ConfigWriter.write(config, file, None)
            file
        }.toList
    }
  }

  /**
   * The files that would be generated
   */
  def materializedFiles() : Try[Seq[File]] = Try {
    val dir = materializedDir
    OverlayConfigCompanion.aggregateGeneratedConfigs2(overlays.flatMap(_.generatedConfigs)) match {
      case Left(issues) =>
        sys.error("Cannot materialize invalid or inconsistent overlays. Issues: " + issues.mkString(";"))
      case Right(configByFile) =>
        configByFile.map {
          case (fileName, config) => new File(dir, fileName)
        }.toList
    }
  }

  def isMaterialized() : Boolean = {
    materializedFiles() match {
      case Success(files) => files.forall { f => f.exists() && f.isFile() }
      case _              => false
    }
  }

  /**
   * Return the aggregated properties of the overlay configs.
   * You need to ensure there are no conflicting properties with `validate`,
   * otherwise some properties might be override and thus lost.
   *
   * @return The properties map.
   */
  def properties : Map[String, String] = {
    overlays.toSeq.sorted.flatMap(o => o.properties).toMap
  }

}

final object LocalOverlays {

  private[this] lazy val log = Logger[LocalOverlays.type]

  def materializedDir(overlays : Iterable[OverlayRef], profileDir : File) : File = {
    if (overlays.isEmpty) {
      profileDir
    } else {
      val overlayParts = overlays.toList.map(o => s"${o.name}-${o.version}").distinct.sorted
      new File(profileDir, overlayParts.mkString("/"))
    }
  }

  def preferredConfigFile(overlays : Iterable[OverlayRef], profileDir : File) : File = {
    val overlayPart =
      if (overlays.isEmpty) "base"
      else overlays.toList.map(o => s"${o.name}-${o.version}").distinct.sorted.mkString("_")

    new File(profileDir, s"overlays/${overlayPart}.conf")
  }

  def read(config : Config, profileDir : File) : Try[LocalOverlays] = Try {
    LocalOverlays(
      profileDir = profileDir,
      overlays = config.getObjectList("overlays").asScala.map { c =>
        OverlayConfigCompanion.read(c.toConfig()).get
      }.toSet
    )
  }

  def toConfig(localOverlays : LocalOverlays) : Config = {
    val configs = localOverlays.overlays.toList.sorted.map { o =>
      val config = OverlayConfigCompanion.toConfig(o)
      config.root()
    }
    val configs2 = ConfigValueFactory.fromIterable(configs.asJava)
    ConfigFactory.empty().withValue("overlays", configs2)
  }

  /**
   * Find all local overlays installed under the given `profileDir`.
   */
  def findLocalOverlays(profileDir : File) : List[LocalOverlays] = {
    val overlaysDir = new File(profileDir, "overlays")
    val candidates = Option(overlaysDir.listFiles()).getOrElse(Array()).filter(f => f.isFile() && f.getName().endsWith(".conf"))
    log.debug(s"About to parse found files and find valid local overlays: ${candidates.mkString(", ")}")
    val localOverlays = candidates.toList.flatMap { file =>
      val overlay = Try(ConfigFactory.parseFile(file)).flatMap(c => LocalOverlays.read(c, profileDir))
      overlay match {
        case Success(localOverlays) =>
          log.debug("Found local overlays: " + localOverlays)
          List(localOverlays)
        case Failure(e) =>
          log.error(e)(s"Could not read overlay config file: ${file}")
          List()
      }
    }
    localOverlays
  }

}
