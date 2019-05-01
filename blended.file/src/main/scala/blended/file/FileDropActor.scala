package blended.file

import java.io._
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{GZIPInputStream, ZipInputStream}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.util.ByteString
import blended.util.StreamCopySupport

import scala.util.control.NonFatal

case class FileDropCommand(
  id : String,
  content: ByteString,
  directory: String,
  fileName: String,
  compressed: Boolean,
  append: Boolean,
  timestamp: Long,
  properties: Map[String, Any]
) {

  override def equals(obj: scala.Any): Boolean = obj match {
    case cmd : FileDropCommand =>
      content.sameElements(cmd.content) &&
      directory.equals(cmd.directory) &&
      fileName.equals(cmd.fileName) &&
      compressed == cmd.compressed &&
      append == cmd.append &&
      timestamp == cmd.timestamp &&
      properties.equals(cmd.properties)
    case _ => false
  }

  override def toString: String = {

    val ts = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS").format(new Date(timestamp))
    s"FileDropCommand[$id](dir = [$directory], fileName = [$fileName], compressed = $compressed, append = $append, timestamp = [$ts], content-size = ${content.length}), " +
    s"properties=${properties.mkString("[", ",", "]")}"
  }
}

object FileDropResult {
  def result(cmd: FileDropCommand, error: Option[Throwable]): FileDropResult = new FileDropResult(
    cmd.copy(content = ByteString("")), error
  )
}

case class FileDropResult(cmd: FileDropCommand, error: Option[Throwable])

class FileDropActor extends Actor with ActorLogging {

  def checkDirectory(dir: File) : Boolean = {

    if (!dir.exists()) {
      log.debug(s"Creating directory [${dir.getAbsolutePath}]")
      dir.mkdirs()
    }

    dir.exists() && dir.isDirectory && dir.canWrite
  }

  def finalFile(cmd: FileDropCommand) : File = {

    val file = new File(cmd.directory, cmd.fileName)

    if (!cmd.append) {
      if (file.exists()) {
        // In case we need to generate a new file name
        new File(cmd.directory, cmd.fileName.lastIndexOf('.') match {
          case -1 => s"dup_${System.currentTimeMillis()}_${cmd.fileName}"
          case pos => s"${cmd.fileName.substring(0, pos)}.dup_${System.currentTimeMillis()}${cmd.fileName.substring(pos)}"
        })
      } else {
        // In case we do not append and we can generate a new file
        file
      }
    } else {
      // In case we append the content to a file, we keep the same final file name
      file
    }
  }

  // A temp file is only created when we need to append to an existing file.
  def tmpFile(cmd: FileDropCommand) : Option[File] = {
    if (cmd.append) {
      val file = finalFile(cmd)
      if (file.exists()) {
        val tmpName = s"${cmd.fileName}.${cmd.timestamp}.tmp"
        val tmpFile = new File(cmd.directory, tmpName)
        log.debug(s"Creating temporary file [${tmpFile.getAbsolutePath}]")
        file.renameTo(tmpFile)
        Some(tmpFile)
      } else {
        None
      }
    } else {
      None
    }
  }

  // The outfile is the file that will temporarily hold the final content
  def outFile(cmd: FileDropCommand) : File =
    new File(cmd.directory, s"${cmd.fileName}.${cmd.timestamp}.out")

  def prepareOutputStream(cmd: FileDropCommand, tmpFile: Option[File]) : OutputStream = {

    val of = outFile(cmd)

    if (!cmd.append) {
      new FileOutputStream(of)
    } else {
      tmpFile match {
        case None => new FileOutputStream(of)
        case Some(tf) =>
          Files.copy(tf.toPath, of.toPath)
          new FileOutputStream(of, true)
      }
    }
  }

  def inputStream(cmd : FileDropCommand) : Option[InputStream] = {
    if (cmd.compressed) {

      try {
        log.debug("Trying to use GZIP compression")
        Some(new GZIPInputStream(new BufferedInputStream(new ByteArrayInputStream(cmd.content.toArray))))
      } catch {
        case NonFatal(_) =>
          log.debug("Trying to use ZIP compression")
          val zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(cmd.content.toArray)))
          val next = Option(zis.getNextEntry)
          if (next.isEmpty) None else Some(zis)
      }
    } else {
      log.debug(s"Writing content without compression to ${outFile(cmd)}")
      Some(new ByteArrayInputStream(cmd.content.toArray))
    }
  }

  private[this] def respond(requestor: ActorRef, cmd: FileDropCommand, t : Option[Throwable] = None) : Unit = {

    val fdr = FileDropResult.result(cmd, t)
    requestor ! fdr
  }

  override def receive: Receive = {

    case cmd : FileDropCommand =>

      val requestor = sender()
      val outdir = new File(cmd.directory)

      var tf : Option[File] = None

      var is : Option[InputStream] = None
      var os : Option[OutputStream] = None

      if (checkDirectory(outdir)) {

        try {
          tf = tmpFile(cmd)

          os = Some(prepareOutputStream(cmd, tf))
          is = inputStream(cmd)

          is match {
            case Some(input) =>
              try {
                StreamCopySupport.copyStream(input, os.get)
              } finally {
                try {
                  is.foreach(_.close())
                } finally {
                  os.foreach(_.close())
                }
              }

              val ff = finalFile(cmd)
              outFile(cmd).renameTo(ff)
              tf.foreach{ f => f.delete() }

              log.info(s"Successfully executed [$cmd] and created file [${ff.getAbsolutePath}]")
              respond(requestor, cmd)

            case None =>
              throw new Exception(s"InputStream for command [$cmd] not resolved.")
          }


        } catch {
          case NonFatal(t) =>
            log.warning(s"Error executing $cmd: ${t.getMessage}")

            tf.foreach { f => f.renameTo(new File(cmd.directory, cmd.fileName)) }
            outFile(cmd).delete()

            respond(requestor, cmd, Some(t))
        }
      } else {
        val msg = s"The directory [${outdir.getAbsolutePath}] does not exist or is not writable."
        log.warning(msg)
        respond(requestor, cmd, Some(new Exception(msg)))
      }
  }
}
