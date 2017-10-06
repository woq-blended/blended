package blended.mgmt.repo.file

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Formatter

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.util.Try
import scala.util.control.NonFatal

import blended.mgmt.repo.ArtifactRepo
import blended.mgmt.repo.WritableArtifactRepo
import java.io.FileOutputStream
import blended.updater.config.util.StreamCopy
import org.slf4j.LoggerFactory

class FileArtifactRepo(override val repoId: String, baseDir: File)
    extends ArtifactRepo
    with WritableArtifactRepo {
  import FileArtifactRepo._

  private[this] val log = LoggerFactory.getLogger(classOf[FileArtifactRepo])

  def withCheckedFilePath[T](path: String)(f: File => T): Try[T] = Try {
    val base = baseDir.toURI().normalize()
    val toCheck = new File(baseDir, path).toURI().normalize()
    if (!toCheck.getPath().startsWith(base.getPath())) sys.error("invalid path given")
    val pathToCheck = toCheck.getPath().substring(base.getPath().length())
    if (pathToCheck.startsWith("..") || pathToCheck.startsWith("/..")) sys.error("invalid path given")
    new File(toCheck)
  }.map(f)

  def findFile(path: String): Option[File] = {
    withCheckedFilePath(path) { file =>
      if (file.exists() && file.isFile()) Option(file) else None
    }.getOrElse(None)
  }

  def findFileSha1Checksum(path: String): Option[String] = {
    findFile(path) flatMap { file =>
      val sha1Stream = new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), MessageDigest.getInstance("SHA"))
      try {
        while (sha1Stream.read != -1) {}
        Option(bytesToString(sha1Stream.getMessageDigest.digest))
      } catch {
        case NonFatal(e) => None
      } finally {
        sha1Stream.close()
      }
    }
  }

  override def toString(): String = getClass().getSimpleName() + "(repoId=" + repoId + ",baseDir=" + baseDir + ")"

  def listFiles(path: String): Iterator[String] = {
    val base = baseDir.toURI().normalize()
    withCheckedFilePath(path) { file =>
      if (!file.exists()) Iterator.empty else {

        def getFiles(dir: Path): Iterator[Path] = {
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

  override def uploadFile(path: String, fileContent: InputStream, sha1Sum: Option[String]): Try[Unit] =
    withCheckedFilePath(path) { file =>
      if (file.exists()) {
        if (file.isFile()) {
          // collision
          // no checksum or checksum differs means we cannot ensure the artifact is identical, so we abort
          if (sha1Sum.isEmpty || sha1Sum != findFileSha1Checksum(path)) {
            throw new ArtifactCollisionException("There is already an artifact installed under path:" + path)
          }
          // else nothing to do
          log.info("Artifact with same checksum already present: " + path)

        } else {
          log.error("Artifact path: {} is a directory. Cannot upload", path)
          // e.g. an existing directory
          throw new IllegalArgumentException("The given path [" + path + "] cannot be used as artifact path")
        }

      } else {
        // file does not exists, installing now
        Option(file.getParentFile()).map(_.mkdirs())
        val fos = new FileOutputStream(file)
        try {
          log.debug("About to save file: {}", file)
          StreamCopy.copy(fileContent, fos)
        } finally {
          fos.close()
        }
      }

    }

}

object FileArtifactRepo {

  def bytesToString(digest: Array[Byte]): String = {
    import java.lang.StringBuilder
    val result = new StringBuilder(32);
    val f = new Formatter(result)
    digest.foreach(b => f.format("%02x", b.asInstanceOf[Object]))
    result.toString
  }

  class ArtifactCollisionException(msg: String) extends RuntimeException(msg)

}