package blended.mgmt.repo.file

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Formatter

import scala.util.control.NonFatal

import blended.mgmt.repo.ArtifactRepo

class FileArtifactRepo(override val repoId: String, baseDir: File) extends ArtifactRepo {

  def findFile(path: String): Option[File] = {
    val file = new File(baseDir, path)
    if (file.exists()) Option(file) else None
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

}