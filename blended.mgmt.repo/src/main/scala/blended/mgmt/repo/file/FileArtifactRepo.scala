package blended.mgmt.repo.file

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Formatter

import scala.util.control.NonFatal

import blended.mgmt.repo.ArtifactRepo
import java.nio.file.Path
import java.nio.file.Files
import scala.collection.JavaConverters._
import scala.util.Try

class FileArtifactRepo(override val repoId: String, baseDir: File) extends ArtifactRepo {

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

  def bytesToString(digest: Array[Byte]): String = {
    import java.lang.StringBuilder
    val result = new StringBuilder(32);
    val f = new Formatter(result)
    digest.foreach(b => f.format("%02x", b.asInstanceOf[Object]))
    result.toString
  }

  override def toString(): String = getClass().getSimpleName() + "(repoId=" + repoId + ",baseDir=" + baseDir + ")"

  def listFiles(path: String): Iterator[String] = {
    val base = baseDir.toURI().normalize()
    withCheckedFilePath(path) { file =>
      if (!file.exists()) Iterator.empty else {
        val fs = file.toPath().getFileSystem()

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

}