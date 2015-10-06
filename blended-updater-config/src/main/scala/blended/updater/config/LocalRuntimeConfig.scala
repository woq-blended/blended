package blended.updater.config

import java.io.PrintStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.File
import scala.util.Try
import scala.io.Source
import java.util.Properties
import java.io.FileReader
import scala.collection.immutable._

case class LocalRuntimeConfig(runtimeConfig: RuntimeConfig, baseDir: File) {

  def bundleLocation(bundle: BundleConfig): File = RuntimeConfig.bundleLocation(bundle, baseDir)

  def bundleLocation(artifact: Artifact): File = RuntimeConfig.bundleLocation(artifact, baseDir)

  val profileFileLocation: File = new File(baseDir, "profile.conf")

  def resourceArchiveLocation(resourceArchive: Artifact): File =
    RuntimeConfig.resourceArchiveLocation(resourceArchive, baseDir)

  def resourceArchiveTouchFileLocation(resourceArchive: Artifact): File =
    RuntimeConfig.resourceArchiveTouchFileLocation(resourceArchive, baseDir, runtimeConfig.mvnBaseUrl)

  val propertiesFileLocation: Option[File] =
    runtimeConfig.properties.get(RuntimeConfig.Properties.PROFILE_PROPERTY_FILE).map(f => new File(baseDir, f))

  def createResourceArchiveTouchFile(resourceArchive: Artifact, resourceArchiveChecksum: Option[String]): Try[File] = Try {
    val file = resourceArchiveTouchFileLocation(resourceArchive)
    Option(file.getParentFile()).foreach { parent =>
      parent.mkdirs()
    }
    val os = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))
    try {
      os.println(resourceArchiveChecksum.getOrElse(""))
    } finally {
      os.close()
    }
    file
  }

  def validate(includeResourceArchives: Boolean,
    explodedResourceArchives: Boolean,
    checkPropertiesFile: Boolean): Seq[String] = {

    val configIssues = runtimeConfig.validate()

    val artifacts = runtimeConfig.allBundles.map(b => bundleLocation(b) -> b.artifact) ++
      (if (includeResourceArchives) runtimeConfig.resources.map(r => resourceArchiveLocation(r) -> r) else Seq())

    val artifactIssues = artifacts.par.flatMap {
      case (file, artifact) =>
        val issue = if (!file.exists()) {
          Some(s"Missing file: ${file.getName()}")
        } else {
          RuntimeConfig.digestFile(file) match {
            case Some(d) =>
              if (Option(d) != artifact.sha1Sum) {
                Some(s"Invalid checksum of bundle jar: ${file.getName()}")
              } else None
            case None =>
              Some(s"Could not evaluate checksum of bundle jar: ${file.getName()}")
          }
        }
        issue.toList
    }.seq

    val resourceIssues = if (explodedResourceArchives) {
      runtimeConfig.resources.flatMap { artifact =>
        val touchFile = resourceArchiveTouchFileLocation(artifact)
        if (touchFile.exists()) {
          val persistedChecksum = Source.fromFile(touchFile).getLines().mkString("\n")
          if (persistedChecksum != artifact.sha1Sum) {
            List(s"Resource ${artifact.fileName} was unpacked from an archive with a different checksum.")
          } else Nil
        } else {
          List(s"Resource ${artifact.fileName.getOrElse(runtimeConfig.resolveFileName(artifact.url).get)} not unpacked")
        }
      }
    } else Nil

    // TODO: check for mandatory properties
    val propertyIssues = if (checkPropertiesFile) {
      propertiesFileLocation match {
        case None => Nil
        case Some(file) =>
          val mandatoryProps = runtimeConfig.properties.get(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS).
            toList.flatMap(_.split("[,]"))
          lazy val props = {
            val p = new Properties()
            Try { p.load(new FileReader(file)) }
            p
          }
          val missing = mandatoryProps.filter(prop => Option(props.get(prop)).isEmpty)
          missing.map(p => s"Missing mandatory property [$p] in properties file: [$file]")
      }
    } else Nil

    val issues = configIssues ++ artifactIssues ++ resourceIssues ++ propertyIssues
    issues
  }
}