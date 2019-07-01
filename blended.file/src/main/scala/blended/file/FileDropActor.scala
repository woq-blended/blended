package blended.file

import java.io._
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{GZIPInputStream, ZipInputStream}

import akka.actor.{Actor, ActorRef}
import akka.util.ByteString
import blended.util.logging.Logger

import scala.util.Try
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
case object FileDropChunk
case class FileDropAbort(t:Throwable)

class FileDropActor extends Actor {

  /**
    *
    * @param requestor The actor which has requested the filedrop and expects a response.
    * @param cmd The FileDropCommand to execute.
    * @param tmpFile If in append mode, tmpFile will point to a copy of the original file
    * @param outFile The file holding the output
    * @param is The stream that has to be copied to the out file.
    * @param os The out stream used to write to the out file.
    */
  case class FileDropState(
    requestor : ActorRef,
    cmd : FileDropCommand,
    tmpFile : Option[File],
    outFile : File,
    is : Option[InputStream],
    os : Option[OutputStream]
  )

  private val log : Logger = Logger[FileDropActor]

  // scalastyle:off magic.number
  private val buffer : Array[Byte] = new Array[Byte](4096)
  // scalastyle:on magic.number

  override def preStart(): Unit = context.become(idle)

  private def checkDirectory(dir: File) : Boolean = {

    if (!dir.exists()) {
      log.debug(s"Creating directory [${dir.getAbsolutePath}]")
      dir.mkdirs()
    }

    dir.exists() && dir.isDirectory && dir.canWrite
  }

  // determine the final file name for a file drop
  private def finalFile(cmd: FileDropCommand) : File = {

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

  // prepare the output stream, if required for append
  def prepareOutputStream(cmd: FileDropCommand) : (OutputStream, Option[File], File) = {

    val tf = tmpFile(cmd)

    val of : File = outFile(cmd)

    val outStream = if (!cmd.append) {
      new FileOutputStream(of)
    } else {
      tf match {
        case None => new FileOutputStream(of)
        case Some(f) =>
          Files.copy(f.toPath, of.toPath)
          log.debug(s"Appending to file [${of.getAbsolutePath()}]")
          new FileOutputStream(of, true)
      }
    }

    (outStream, tf, of)
  }

  // Create the input stream, potentially from a zip stream
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

  private[this] def respond(
    state : FileDropState,
    t : Option[Throwable] = None
  ) : Unit = {

    Try(state.is.foreach(_.close()))
    Try(state.os.foreach(_.close()))

    val fdr = FileDropResult.result(state.cmd, t)

    if (t.isDefined) {
      // in case an error was encountered, we will restore the original file
      // and forget the append
      state.tmpFile.foreach { tf => tf.renameTo(finalFile(state.cmd)) }
      state.outFile.delete()
    } else {
      // In case the command was successful, we will delete the tmpfile
      // and create the final file
      state.tmpFile.foreach(_.delete())
      state.outFile.renameTo(finalFile(state.cmd))
    }
    state.requestor ! fdr

    context.become(idle)
  }

  override def receive: Receive = Actor.emptyBehavior

  // While in dropping state we drop one chunk after the other
  private def dropping(state : FileDropState) : Receive = {
    case cmd : FileDropCommand =>
      sender() ! FileDropResult(cmd, Some(new Exception("Filedropper is busy")))

    // Abort the file drop
    case FileDropAbort(t) =>
      respond(state, Some(t))

    // Drop the next chunk to the out directory
    case FileDropChunk =>
      log.debug("Dropping chunk")
      (state.is, state.os) match {
        // Streams are still open, so we can proceed
        case (Some(in), Some(out)) =>
          try {
            val cnt = in.read(buffer)
            if (cnt >= 0) {
              out.write(buffer, 0, cnt)
              self ! FileDropChunk
            } else {
              log.info(s"Successfully executed [${state.cmd}] and created file [${finalFile(state.cmd).getAbsolutePath}]")
              respond(state, None)
            }
          } catch {
            case NonFatal(e) =>
              log.warn(e)(s"Error while dropping file [${e.getMessage()}]")
              respond(state, Some(e))
          }
        // The input or output stream is not open
        case (_,_) =>
          respond(state, Some(new Exception("Illegal stream state in FileDropActor")))
      }
  }

  private def idle : Receive = {

    case FileDropChunk =>
    case FileDropAbort(_) =>

    case cmd : FileDropCommand =>

      val requestor = sender()
      val outdir = new File(cmd.directory)

      var tf : Option[File] = None

      var is : Option[InputStream] = None
      var os : Option[OutputStream] = None

      if (checkDirectory(outdir)) {

        try {
          val (s, tf, of) = prepareOutputStream(cmd)
          os = Some(s)
          is = inputStream(cmd)

          val state : FileDropState = FileDropState(
            requestor = requestor,
            cmd = cmd,
            tmpFile = tf,
            outFile = of,
            is = is,
            os = os
          )

          context.become(dropping(state))
          self ! FileDropChunk
        } catch {
          case NonFatal(t) =>
            log.warn(s"Error executing $cmd: ${t.getMessage}")

            respond(FileDropState(
              requestor = requestor,
              cmd = cmd,
              tmpFile = tf,
              outFile = outFile(cmd),
              is = is,
              os = os
            ), Some(t))
        }
      } else {
        val msg = s"The directory [${outdir.getAbsolutePath}] does not exist or is not writable."
        log.warn(msg)
        respond(FileDropState(
          requestor = requestor,
          cmd = cmd,
          tmpFile = tf,
          outFile = outFile(cmd),
          is = is,
          os = os
        ), Some(new Exception(msg)))
      }
  }
}
