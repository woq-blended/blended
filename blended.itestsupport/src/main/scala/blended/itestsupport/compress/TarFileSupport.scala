package blended.itestsupport.compress

import java.io._

import scala.collection.mutable

import blended.util.io.StreamCopy
import blended.util.logging.Logger
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}

object TarFileSupport {

  private[this] val log = Logger[TarFileSupport]

  def untar(is: InputStream): Map[String, Array[Byte]] = {
    val tar = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(is))

    val content: mutable.Map[String, Array[Byte]] = mutable.Map.empty
    var entry = Option(tar.getNextEntry())

    while (entry.isDefined) {
      val bos = new ByteArrayOutputStream()
      StreamCopy.copy(tar, bos)

      bos.close()

      log.debug(s"Extracted [${entry.get.getName()}], size [${bos.size}].")
      content.put(entry.get.getName(), bos.toByteArray())

      entry = Option(tar.getNextEntry())
    }

    tar.close()
    is.close()

    content.toMap
  }

  def tar(file: File, os: OutputStream, user: Int = 0, group: Int = 0): Unit = {

    def addFileToTar(tarOs: TarArchiveOutputStream, file: File, base: String): Unit = {
      val entryName = base + file.getName()
      val entry = new TarArchiveEntry(file, entryName)

      entry.setUserId(user)
      entry.setGroupId(group)

      log.info(s"Adding [$entryName] to tar archive with [user($user), group($group)].")

      tarOs.putArchiveEntry(entry)

      if (file.isFile()) {
        StreamCopy.copy(new FileInputStream(file), tarOs)
        tarOs.closeArchiveEntry()
      } else {
        tarOs.closeArchiveEntry()

        val files = Option(file.listFiles())
        files.map { ff =>
          ff.foreach { f =>
            addFileToTar(tarOs, f, entryName + "/")
          }
        }
      }
    }

    if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath())

    val bOut = new BufferedOutputStream(os)
    val tarOut = new TarArchiveOutputStream(bOut)

    try {
      addFileToTar(tarOut, file, "")
    } finally {
      tarOut.finish()
      tarOut.close()
      bOut.close()
      os.close()
    }
  }
}

class TarFileSupport
