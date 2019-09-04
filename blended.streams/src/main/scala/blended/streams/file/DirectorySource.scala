package blended.streams.file

import java.io.File
import java.nio.file.{DirectoryStream, Files, Path}

import scala.collection.JavaConverters._
import blended.util.logging.Logger

import scala.util.control.NonFatal

class DirectorySource(pollCfg : FilePollConfig) {

  private val log : Logger = Logger[DirectorySource]
  private var pendingFiles : Map[File, Option[Long]] = Map.empty

  private val srcDir = new File(pollCfg.sourceDir)

  // First we make sure the poll directory exists
  if (!srcDir.exists()) {
    srcDir.mkdirs()
  }

  def nextFile() : Option[File] = {
    // If the directory does not yet exist or is not readable, we log a warning
    if (!srcDir.exists() || !srcDir.isDirectory() || !srcDir.canRead()) {
      log.warn(s"Directory [$srcDir] for [${pollCfg.id}] does not exist or is not readable.")
      None
    } else {

      // first we clear all entries from the pending files that have been delivered to a file poller longer
      // than the poll interval ago
      cleanup()

      // If we don't have a current file to deliver we will scan the directory again
      if (file().isEmpty) {
        scanDirectory()
      }

      val result : Option[File] = file() match {
        case None => None
        case Some(f) =>
          pendingFiles = pendingFiles.filterKeys(_ != f) + (f -> Some(System.currentTimeMillis()))
          Some(f)
      }

      result
    }
  }

  private def cleanup() : Unit = {
    val now : Long = System.currentTimeMillis()
    pendingFiles = pendingFiles.filter {
      case (_, Some(v)) =>  now - v < pollCfg.interval.toMillis
      case (_, None) => true
    }
  }

  private def scanDirectory() : Unit = {
    log.info(s"Executing directory scan for directory [${pollCfg.sourceDir}] with pattern [${pollCfg.pattern}]")

    val filter : DirectoryStream.Filter[Path] = new DirectoryStream.Filter[Path] {
      override def accept(entry: Path): Boolean = {
        entry.getParent().toFile().equals(srcDir) &&
          pollCfg.pattern.forall(p => entry.toFile().getName().matches(p)) &&
          !pendingFiles.contains(entry.toFile())
      }
    }

    val dirStream : DirectoryStream[Path] = Files.newDirectoryStream(srcDir.toPath(), filter)

    try {
      // scalastyle:off magic.number
      dirStream.iterator().asScala.take(100).map(_.toFile()).foreach(f => pendingFiles += (f -> None))
      // scalastyle:on magic.number
    } catch {
      case NonFatal(e) =>
        log.warn(s"Error reading directory [${srcDir.getAbsolutePath()}] : [${e.getMessage()}]")
        None
    } finally {
      dirStream.close()
    }
  }

  private def file() : Option[File] = pendingFiles.find{ case (_, v) => v.isEmpty }.map(_._1)
}
