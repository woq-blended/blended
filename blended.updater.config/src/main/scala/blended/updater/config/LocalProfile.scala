package blended.updater.config

import java.io._

import scala.io.Source
import scala.util.Try

case class LocalProfile(
    resolvedProfile: ResolvedProfile,
    baseDir: File
) {

  def runtimeConfig = resolvedProfile.profile

  def bundleLocation(bundle: BundleConfig): File = ProfileCompanion.bundleLocation(bundle, baseDir)

  def bundleLocation(artifact: Artifact): File = ProfileCompanion.bundleLocation(artifact, baseDir)

  val profileFileLocation: File = new File(baseDir, "profile.conf")

  def resourceArchiveLocation(resourceArchive: Artifact): File =
    ProfileCompanion.resourceArchiveLocation(resourceArchive, baseDir)

  def resourceArchiveTouchFileLocation(resourceArchive: Artifact): File =
    ProfileCompanion.resourceArchiveTouchFileLocation(resourceArchive, baseDir, runtimeConfig.mvnBaseUrl)

  def createResourceArchiveTouchFile(resourceArchive: Artifact, resourceArchiveChecksum: Option[String]): Try[File] =
    Try {
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
      explodedResourceArchives: Boolean
  ): Try[Seq[String]] = Try {

    val artifacts = resolvedProfile.allBundles.get.map(b => bundleLocation(b) -> b.artifact) ++
      (if (includeResourceArchives) runtimeConfig.resources.map(r => resourceArchiveLocation(r) -> r) else Seq())

    val artifactIssues = {
      var checkedFiles: Map[File, String] = Map()
      artifacts.flatMap {
        case (file, artifact) =>
          val issue = if (!file.exists()) {
            Some(s"Missing file: ${file.getName()}")
          } else {
            checkedFiles.get(file).orElse(ProfileCompanion.digestFile(file)) match {
              case Some(d) =>
                checkedFiles += file -> d
                if (Option(d) != artifact.sha1Sum) {
                  Some(s"Invalid checksum of bundle jar: ${file.getName()}")
                } else None
              case None =>
                Some(s"Could not evaluate checksum of bundle jar: ${file.getName()}")
            }
          }
          issue
      }
    }

    val resourceIssues = if (explodedResourceArchives) {
      runtimeConfig.resources.flatMap { artifact =>
        val touchFile = resourceArchiveTouchFileLocation(artifact)
        if (touchFile.exists()) {
          val persistedChecksum = Source.fromFile(touchFile).getLines().mkString("\n")
          if (artifact.sha1Sum.isDefined && persistedChecksum != artifact.sha1Sum.get) {
            List(
              s"Resource ${artifact.fileName.getOrElse(runtimeConfig.resolveFileName(artifact.url).get)} was unpacked from an archive with a different checksum (${persistedChecksum}).")
          } else Nil
        } else {
          List(s"Resource ${artifact.fileName.getOrElse(runtimeConfig.resolveFileName(artifact.url).get)} not unpacked")
        }
      }
    } else Nil

    artifactIssues ++ resourceIssues
  }
}
