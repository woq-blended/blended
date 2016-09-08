package blended.updater.internal

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import scala.collection.immutable.List
import scala.collection.immutable.Nil
import scala.collection.immutable.Seq
import scala.util.Try
import org.slf4j.LoggerFactory
import scala.util.control.NonFatal

object Unzipper extends Unzipper {
  // TODO: add failMissingProperty 
  case class PlaceholderConfig(
    openSequence: String,
    closeSequence: String,
    escapeChar: Char,
    properties: Map[String, String],
    failOnMissing: Boolean)
}

class Unzipper {
  import Unzipper._

  private[this] val log = LoggerFactory.getLogger(classOf[Unzipper])

  def unzip(archive: File, targetDir: File, selectedFiles: String*): Try[Seq[File]] = {
    unzip(archive, targetDir, selectedFiles.map(f => (f, null)).toList, None, None)
  }

  def unzip(archive: File, targetDir: File, _selectedFiles: List[(String, File)]): Try[Seq[File]] = {
    unzip(archive, targetDir, _selectedFiles, None, None)
  }

  /**
   * Extract files from a ZIP archive.
   * If the list of `selectedFiles` is empty and no `fileSelector` was given, than all files will be extracted.
   *
   * @param archive The file denoting a ZIP archive.
   * @param targetDir The base directory, where the extracted files will be stored.
   * @param selectedFiles A list of name-file pairs denoting which archive content should be extracted into which file.
   *   The name of the path inside the archive.
   *   The file will be the place that file will be extracted to.
   *   If the file value is `null`, that the file will be extracted into the `targetDir` without any sub directory created.
   * @param fileSelector A filter used to decide if a file in the archive should be extracted or not.
   *   `fileSelector` is not able to exclude files already selected with `selectedFiles`.
   *   If a selector is given (`[scala.Some]`), than only those files will be extracted, for which the selector returns `true`.
   *
   * @return A `Seq` of all extracted files.
   */
  def unzip(archive: File,
    targetDir: File,
    selectedFiles: List[(String, File)],
    fileSelector: Option[String => Boolean],
    placeholderReplacer: Option[PlaceholderConfig]): Try[Seq[File]] = Try {

    if (!archive.exists() || !archive.isFile()) throw new FileNotFoundException("Zip file cannot be found: " + archive)
    targetDir.mkdirs

    log.debug("Extracting zip archive {} to {}", Array(archive, targetDir): _*)

    val partial = !selectedFiles.isEmpty || fileSelector.isDefined
    if (partial) log.debug("Only extracting some content of zip file")

    val fileWriter: (InputStream, OutputStream) => Unit = placeholderReplacer match {
      case None => copy _
      case Some(PlaceholderConfig(openSeq, closeSeq, escapeChar, props, failOnMissing)) =>
        val pp = new PlaceholderProcessor(props, openSeq, closeSeq, escapeChar, failOnMissing)
        (in, out) => pp.process(in, out).get
    }

    def findName(name: String): String = {
      val index = name.lastIndexOf("/")
      if (index < 0) name else name.substring(index)
    }

    /**
     * Test if a file is accepted and calculate the output file name
     */
    def acceptFile(file: String): Option[File] = {
      if (!partial) {
        // unpack all
        Some(new File(targetDir, file))
      } else {
        // select
        val selectedFile = selectedFiles.find {
          case (name, target) => file == name
        } map {
          case (name, target) =>
            Option(target).getOrElse(new File(targetDir, findName(name)))
        }

        selectedFile.orElse {
          fileSelector match {
            case None => None
            case Some(s) =>
              val select = s.apply(file)
              if (select) Some(new File(targetDir, file)) else None
          }
        }
      }
    }

    var filesToExtract = selectedFiles
    var extractedFilesInv: List[File] = Nil

    try {
      val zipIs = new ZipInputStream(new FileInputStream(archive))
      var zipEntry = zipIs.getNextEntry
      val finished = partial && fileSelector.isEmpty && filesToExtract.isEmpty
      while (zipEntry != null && !finished) {
        val extractFile: Option[File] = if (partial) {
          if (zipEntry.isDirectory) {
            acceptFile(zipEntry.getName).foreach { dir =>
              log.debug("  Creating {}", dir.getName())
              dir.mkdirs
            }
            None
          } else {
            acceptFile(zipEntry.getName)
          }
        } else {
          if (zipEntry.isDirectory) {
            log.debug("  Creating {}", zipEntry.getName)
            new File(targetDir + "/" + zipEntry.getName).mkdirs
            None
          } else {
            Some(new File(targetDir + "/" + zipEntry.getName))
          }
        }

        if (extractFile.isDefined) {
          log.debug("  Extracting {}", zipEntry.getName)
          val targetFile = extractFile.get
          if (targetFile.exists
            && !targetFile.getParentFile.isDirectory) {
            throw new RuntimeException(
              "Expected directory is a file. Cannot extract zip content: "
                + zipEntry.getName)
          }
          // Ensure, that the directory exists
          targetFile.getParentFile.mkdirs
          val outputStream = new BufferedOutputStream(new FileOutputStream(targetFile))
          try {
            fileWriter(zipIs, outputStream)
          } finally {
            outputStream.close
          }
          extractedFilesInv ::= targetFile
          if (zipEntry.getTime > 0) {
            targetFile.setLastModified(zipEntry.getTime)
          }
        }

        zipEntry = zipIs.getNextEntry()
      }

      zipIs.close
    } catch {
      //      case e: IOException =>
      //        throw new RuntimeException("Could not unzip file: " + archive,
      //          e)
      case NonFatal(e) =>
        throw new RuntimeException("Could not unzip file: " + archive, e)
    }

    if (!filesToExtract.isEmpty) {
      throw new FileNotFoundException(s"""Could not found file "${filesToExtract.head._1}" in zip archive "${archive}".""")
    }

    extractedFilesInv.reverse
  }

  def copy(in: InputStream, out: OutputStream) {
    val buf = new Array[Byte](1024)
    var len = 0
    while ({
      len = in.read(buf)
      len > 0
    }) {
      out.write(buf, 0, len)
    }
  }

}