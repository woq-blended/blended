package blended.file

import java.io._
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{GZIPInputStream, ZipInputStream}

import akka.actor.{Actor, ActorLogging, ActorRef}
import blended.util.StreamCopySupport

import scala.util.control.NonFatal

case class FileDropCommand(
  content: Array[Byte],
  directory: String,
  fileName: String,
  compressed: Boolean,
  append: Boolean,
  timestamp: Long,
  properties: Map[String, Object]
) {
  override def toString: String = {

    val ts = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS").format(new Date(timestamp))
    s"FileDropCommand(dir = [$directory], fileName = [$fileName], compressed = $compressed, append = $append, timestamp = [$ts], content-size = ${content.size})"

  }
}

case class FileDropResult(cmd: FileDropCommand, success: Boolean)

class FileDropActor extends Actor with ActorLogging {

  def checkDirectory(dir: File) : Boolean = {

    if (!dir.exists()) {
      log.debug(s"Creating directory [${dir.getAbsolutePath()}]")
      dir.mkdirs()
    }

    (dir.exists() && dir.isDirectory() && dir.canWrite())
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
        log.debug(s"Creating temporary file [${tmpFile.getAbsolutePath()}]")
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

    val os = new FileOutputStream(outFile(cmd))

    if (cmd.append) {
      tmpFile match {
        case None =>
        case Some(f) =>
          log.debug(s"Copying original content before appending into file ${f.getAbsolutePath()}")
          val tmpIn = new FileInputStream(f)
          StreamCopySupport.copyStream(tmpIn, os)
          tmpIn.close()
      }
    }

    os
  }

  private[this] def respond(requestor: ActorRef, response: FileDropResult) : Unit = {
    context.system.eventStream.publish(response)
    requestor ! response
    context.stop(self)
  }

  override def receive: Receive = {

    case cmd : FileDropCommand =>

      val requestor = sender()
      val outdir = new File(cmd.directory)

      var os : Option[OutputStream] = None
      var tf : Option[File] = None

      if (checkDirectory(outdir)) {

        val is : InputStream = cmd.compressed match {
          case true =>
            log.debug(s"Writing content with compression to ${outFile(cmd)}")

            val zippedIs = try {
              log.debug("Trying to use GZIP compression")
              new GZIPInputStream(new BufferedInputStream(new ByteArrayInputStream(cmd.content)))
            } catch {
              case NonFatal(e) =>
                log.debug("Trying to use ZIP compression")
                val zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(cmd.content)))
                zis.getNextEntry()
                zis
            }
            zippedIs
          case false =>
            log.debug(s"Writing content without compression to ${outFile(cmd)}")
            new ByteArrayInputStream(cmd.content)
        }

        try {
          tf = tmpFile(cmd)
          os = Some(prepareOutputStream(cmd, tf))

          os.foreach{ s =>
            StreamCopySupport.copyStream(is, s)
            s.close()
          }

          val ff = finalFile(cmd)
          outFile(cmd).renameTo(ff)
          tf.foreach{ f => f.delete() }

          log.info(s"Successfully executed [$cmd] and created file [${ff.getAbsolutePath()}]")
          respond(requestor, FileDropResult(cmd, true))

        } catch {
          case NonFatal(t) =>
            log.warning(s"Error executing ${cmd}: ${t.getMessage()}")

            tf.foreach { f => f.renameTo(new File(cmd.directory, cmd.fileName)) }
            outFile(cmd).delete()

            respond(requestor, FileDropResult(cmd, false))
        } finally {
          is.close()
        }
      } else {
        log.warning(s"The directory [${outdir.getAbsolutePath()}] does not exist or is not writable.")
        respond(requestor, FileDropResult(cmd, false))
      }
  }
}
