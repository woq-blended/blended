package blended.updater.config.util

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

import scala.util.Try
import scala.util.control.NonFatal

import blended.util.logging.Logger

object Unzipper extends Unzipper {
  // TODO: add failMissingProperty
  case class PlaceholderConfig(
    openSequence: String,
    closeSequence: String,
    escapeChar: Char,
    properties: Map[String, String],
    failOnMissing: Boolean
  )
}

class Unzipper {
  import Unzipper._

  private[this] val log = Logger[Unzipper]

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
  def unzip(
    archive: File,
    targetDir: File,
    selectedFiles: List[(String, File)],
    fileSelector: Option[String => Boolean],
    placeholderReplacer: Option[PlaceholderConfig]
  ): Try[Seq[File]] = {
    if (!archive.exists() || !archive.isFile()) throw new FileNotFoundException(s"Zip file cannot be found: ${archive}")
    targetDir.mkdirs
    val is = new FileInputStream(archive)
    try {
      unzip(is, targetDir, selectedFiles, fileSelector, placeholderReplacer, Some(archive.getPath()))
    } finally {
      is.close()
    }
  }

  /**
   * Extract files from a ZIP archive.
   * If the list of `selectedFiles` is empty and no `fileSelector` was given, than all files will be extracted.
   *
   * @param inputStream The stream providing a ZIP archive.
   * @param targetDir The base directory, where the extracted files will be stored.
   * @param selectedFiles A list of name-file pairs denoting which archive content should be extracted into which file.
   *   The name of the path inside the archive.
   *   The file will be the place that file will be extracted to.
   *   If the file value is `null`, that the file will be extracted into the `targetDir` without any sub directory created.
   * @param fileSelector A filter used to decide if a file in the archive should be extracted or not.
   *   `fileSelector` is not able to exclude files already selected with `selectedFiles`.
   *   If a selector is given (`[scala.Some]`), than only those files will be extracted, for which the selector returns `true`.
   * @param archive The optional target path of the archive.
   *
   * @return A `Seq` of all extracted files.
   */
  def unzip(
    inputStream: InputStream,
    targetDir: File,
    selectedFiles: List[(String, File)],
    fileSelector: Option[String => Boolean],
    placeholderReplacer: Option[PlaceholderConfig],
    archive: Option[String]
  ): Try[Seq[File]] = Try {

    log.debug(s"Extracting zip archive ${archive.getOrElse("")} to ${targetDir}")

    val partial = !selectedFiles.isEmpty || fileSelector.isDefined
    if (partial) log.debug("Only extracting some content of zip file")

    val fileWriter: (InputStream, OutputStream) => Unit = placeholderReplacer match {
      case None => StreamCopy.copy _
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
      val zipIs = new ZipInputStream(inputStream)
      var zipEntry = zipIs.getNextEntry()
      val finished = partial && fileSelector.isEmpty && filesToExtract.isEmpty
      while (zipEntry != null && !finished) {
        val extractFile: Option[File] = if (partial) {
          if (zipEntry.isDirectory) {
            acceptFile(zipEntry.getName).foreach { dir =>
              log.debug(s"  Creating ${dir.getName()}")
              dir.mkdirs()
            }
            None
          } else {
            acceptFile(zipEntry.getName)
          }
        } else {
          if (zipEntry.isDirectory) {
            log.debug(s"  Creating ${zipEntry.getName()}")
            new File(targetDir + "/" + zipEntry.getName).mkdirs()
            None
          } else {
            Some(new File(targetDir + "/" + zipEntry.getName))
          }
        }

        if (extractFile.isDefined) {
          log.debug(s"  Extracting ${zipEntry.getName()}")
          val targetFile = extractFile.get
          if (targetFile.exists
            && !targetFile.getParentFile().isDirectory()) {
            throw new RuntimeException(
              "Expected directory is a file. Cannot extract zip content: "
                + zipEntry.getName()
            )
          }
          // Ensure, that the directory exists
          targetFile.getParentFile().mkdirs()
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

    } catch {
      case NonFatal(e) =>
        throw new RuntimeException(s"Could not unzip file: ${archive}", e)
    }

    if (!filesToExtract.isEmpty) {
      throw new FileNotFoundException(s"""Could not found file "${filesToExtract.head._1}" in zip archive "${archive}".""")
    }

    extractedFilesInv.reverse
  }

}