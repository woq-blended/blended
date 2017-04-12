package blended.itestsupport.compress

import java.io.{BufferedInputStream, ByteArrayOutputStream, InputStream}

import blended.util.StreamCopySupport
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.slf4j.LoggerFactory

import scala.collection.mutable

object TarFileSupport {

  private[this] val log = LoggerFactory.getLogger(classOf[TarFileSupport])

  def untar(is : InputStream) : Map[String, Array[Byte]] = {
    val tar = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(is))
    val bytes = new Array[Byte](8192)

    val content : mutable.Map[String, Array[Byte]] = mutable.Map.empty
    var entry = Option(tar.getNextEntry())

    while(entry.isDefined) {
      val bos = new ByteArrayOutputStream()
      StreamCopySupport.copyStream(tar, bos)

      bos.close()

      log.debug(s"Extracted [${entry.get.getName()}], size [${bos.size}].")
      content.put(entry.get.getName(), bos.toByteArray())

      entry = Option(tar.getNextEntry())
    }

    tar.close()
    is.close()

    content.toMap
  }
}

class TarFileSupport