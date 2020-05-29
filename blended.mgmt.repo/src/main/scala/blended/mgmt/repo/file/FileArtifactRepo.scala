package blended.mgmt.repo.file

import java.io._
import java.nio.file.{Files, Path}
import java.security.{DigestInputStream, MessageDigest}
import java.util.Formatter

import blended.mgmt.repo.{ArtifactRepo, WritableArtifactRepo}
import blended.updater.config.util.StreamCopy
import blended.util.logging.Logger

import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

class FileArtifactRepo(override val repoId : String, baseDir : File)
  extends ArtifactRepo
  with WritableArtifactRepo {

  import FileArtifactRepo._

  private[this] val log = Logger[this.type]

  /**
   * Only continue when the given `path` is valid.
   */
  def withCheckedFilePath[T](path : String)(f : File => T) : Try[T] = Try {
    val base = baseDir.toURI().normalize()
    val toCheck = new File(baseDir, path).toURI().normalize()
    if (!toCheck.getPath().startsWith(base.getPath())) sys.error("invalid path given")
    val pathToCheck = toCheck.getPath().substring(base.getPath().length())
    if (pathToCheck.startsWith("..") || pathToCheck.startsWith("/..")) sys.error("invalid path given")
    new File(toCheck)
  }.map(f)

  def findFile(path : String) : Option[File] = {
    withCheckedFilePath(path) { file =>
      if (file.exists() && file.isFile()) Option(file) else None
    }.getOrElse(None)
  }

  /**
   * Try to find a file under the given path.
   * @return The checksum of the found file or [[None]] if the file was not found.
   */
  def findFileSha1Checksum(path : String) : Option[String] = {
    findFile(path) flatMap { file =>
      val sha1Stream = new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), MessageDigest.getInstance("SHA"))
      try {
        while (sha1Stream.read != -1) {}
        Option(bytesToString(sha1Stream.getMessageDigest.digest))
      } catch {
        case NonFatal(_) => None
      } finally {
        sha1Stream.close()
      }
    }
  }

  override def toString() : String = getClass().getSimpleName() +
    "(repoId=" + repoId +
    ",baseDir=" + baseDir +
    ")"

  def listFiles(path : String) : Iterator[String] = {
    withCheckedFilePath(path) { file =>
      if (!file.exists()) Iterator.empty else {
        def getFiles(dir : Path) : Iterator[Path] = {
          val files = Files.newDirectoryStream(dir).iterator().asScala
          files.flatMap { f =>
            val isDir = Files.isDirectory(f)
            val file = Iterator(f).filter(f => !isDir)
            val childs = if (isDir) getFiles(f) else Iterator.empty
            file ++ childs
          }
        }

        val files = getFiles(file.toPath())
        val baseFile = file.toURI()
        files.map(f => baseFile.relativize(f.toUri().normalize()).getPath())
      }
    }.getOrElse(Iterator.empty)
  }

  override def uploadFile(path : String, fileContent : InputStream, sha1Sum : Option[String]) : Try[Unit] =
    withCheckedFilePath(path) { file =>
      if (file.exists()) {
        // file already exists
        if (file.isFile()) {
          // collision
          // no checksum or checksum differs means we cannot ensure the artifact is identical, so we abort
          sha1Sum match {
            case None =>
              throw new ArtifactCollisionException(s"There is already an artifact installed under path: $path")
            case sum =>
              val existingSum = findFileSha1Checksum(path)
              if (sum != existingSum) {
                log.info(s"Artifact with different checksum (existing: $existingSum, new: $sum) already present: $path")
                throw new ArtifactCollisionException(s"There is already an artifact with a different checksum installed under path: $path")
              } else {
                // else nothing to do
                log.info(s"Artifact with same checksum already present: $path")
              }
          }
        } else {
          log.error(s"Artifact path [$path] is a directory. Cannot upload")
          // e.g. an existing directory
          throw new IllegalArgumentException(s"The given path [$path] cannot be used as artifact path")
        }
      } else {
        // file does not exists, installing now
        Option(file.getParentFile()).map(_.mkdirs())
        val fos = new FileOutputStream(file)
        try {
          log.debug(s"About to save file: $file")
          StreamCopy.copy(fileContent, fos)
        } finally {
          fos.close()
        }
      }
    }

}

object FileArtifactRepo {

  def bytesToString(digest : Array[Byte]) : String = {
    import java.lang.StringBuilder
    //scalastyle:off magic.number
    val result = new StringBuilder(32)
    //scalastyle:on magic.number
    val f = new Formatter(result)
    digest.foreach(b => f.format("%02x", b.asInstanceOf[Object]))
    result.toString()
  }

  class ArtifactCollisionException(msg : String) extends RuntimeException(msg)

}
