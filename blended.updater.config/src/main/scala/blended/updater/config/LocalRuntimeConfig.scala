package blended.updater.config

import java.io._
import java.util.Properties

import scala.io.Source
import scala.util.Try

case class LocalRuntimeConfig(
    resolvedRuntimeConfig: ResolvedRuntimeConfig,
    baseDir: File) {

  def runtimeConfig = resolvedRuntimeConfig.runtimeConfig

  def bundleLocation(bundle: BundleConfig): File = RuntimeConfigCompanion.bundleLocation(bundle, baseDir)

  def bundleLocation(artifact: Artifact): File = RuntimeConfigCompanion.bundleLocation(artifact, baseDir)

  val profileFileLocation: File = new File(baseDir, "profile.conf")

  def resourceArchiveLocation(resourceArchive: Artifact): File =
    RuntimeConfigCompanion.resourceArchiveLocation(resourceArchive, baseDir)

  def resourceArchiveTouchFileLocation(resourceArchive: Artifact): File =
    RuntimeConfigCompanion.resourceArchiveTouchFileLocation(resourceArchive, baseDir, runtimeConfig.mvnBaseUrl)

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

  def validate(
    includeResourceArchives: Boolean,
    explodedResourceArchives: Boolean,
    checkPropertiesFile: Boolean
  ): Seq[String] = {

    val artifacts = resolvedRuntimeConfig.allBundles.map(b => bundleLocation(b) -> b.artifact) ++
      (if (includeResourceArchives) runtimeConfig.resources.map(r => resourceArchiveLocation(r) -> r) else Seq())

    val artifactIssues = {
      var checkedFiles: Map[File, String] = Map()
      artifacts.par.flatMap {
        case (file, artifact) =>
          val issue = if (!file.exists()) {
            Some(s"Missing file: ${file.getName()}")
          } else {
            checkedFiles.get(file).orElse(RuntimeConfigCompanion.digestFile(file)) match {
              case Some(d) =>
                checkedFiles += file -> d
                if (Option(d) != artifact.sha1Sum) {
                  Some(s"Invalid checksum of bundle jar: ${file.getName()}")
                } else None
              case None =>
                Some(s"Could not evaluate checksum of bundle jar: ${file.getName()}")
            }
          }
          issue.toList
      }.seq
    }

    val resourceIssues = if (explodedResourceArchives) {
      runtimeConfig.resources.flatMap { artifact =>
        val touchFile = resourceArchiveTouchFileLocation(artifact)
        if (touchFile.exists()) {
          val persistedChecksum = Source.fromFile(touchFile).getLines().mkString("\n")
          if (artifact.sha1Sum.isDefined && persistedChecksum != artifact.sha1Sum.get) {
            List(s"Resource ${artifact.fileName.getOrElse(runtimeConfig.resolveFileName(artifact.url).get)} was unpacked from an archive with a different checksum (${persistedChecksum}).")
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

    val issues = artifactIssues ++ resourceIssues ++ propertyIssues
    issues
  }
}