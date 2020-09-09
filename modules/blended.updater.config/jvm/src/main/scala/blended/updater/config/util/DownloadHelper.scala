package blended.updater.config.util

import java.io.File
import blended.util.logging.Logger 
import java.io.{BufferedOutputStream, BufferedInputStream}
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.{Paths, Files}
import scala.util.Try
import scala.util.control.NonFatal
import java.nio.file.StandardCopyOption

object DownloadHelper {

  private val log : Logger = Logger[DownloadHelper.type]

  def download(url: String, file: File): Try[File] =
    Try {
  
      val parentDir = file.getAbsoluteFile().getParentFile() match {
        case null =>
          new File(".")
        case parent =>
          if (!parent.exists()) {
            parent.mkdirs()
          }
          parent
      }

      val tmpFile : File = File.createTempFile(s".${file.getName()}", null, parentDir)
      log.trace(s"About to download [$url] to [${tmpFile.getAbsolutePath()}]")

      try {
        val fileStream = new FileOutputStream(tmpFile)
        val outStream = new BufferedOutputStream(fileStream)
        try {

          val connection = new URL(url).openConnection
          connection.setRequestProperty("User-Agent", "Blended Updater")
          val inStream = new BufferedInputStream(connection.getInputStream())
          try {
            val bufferSize = 1024
            val buffer = new Array[Byte](bufferSize)

            while (
              inStream.read(buffer, 0, bufferSize) match {
                case count if count < 0 => false
                case count => {
                  outStream.write(buffer, 0, count)
                  true
                }
              }
            ) {}
          } finally {
            inStream.close()
          }
        } finally {
          outStream.flush()
          outStream.close()
          fileStream.flush()
          fileStream.close()
        }

        Files.move(Paths.get(tmpFile.toURI()), Paths.get(file.toURI()), StandardCopyOption.ATOMIC_MOVE);

        log.trace(s"Successfully loaded [$url] to [${file.getAbsolutePath()}]")
        file
      } catch {
        case NonFatal(e) =>
          log.warn(s"Error downloading [$url] to [${tmpFile.getAbsolutePath()}] : [${e.getMessage()}]")
          if (tmpFile.exists()) {
            tmpFile.delete()
          }
          throw e
      }
    }
}
