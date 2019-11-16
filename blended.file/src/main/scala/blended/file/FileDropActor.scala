package blended.file

import java.io._
import java.nio.file.{DirectoryStream, Files, Path, StandardCopyOption}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{GZIPInputStream, ZipInputStream}

import akka.actor.{Actor, ActorRef}
import akka.util.ByteString
import blended.util.logging.Logger

import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import StandardCopyOption._

import scala.collection.JavaConverters._

object FileDropCommand {
  val tsPattern : SimpleDateFormat = new SimpleDateFormat("yyyyMMdd.HHmmssSSS")
}

case class FileDropCommand(
  id : String,
  content: ByteString,
  directory: String,
  fileName: String,
  compressed: Boolean,
  append: Boolean,
  timestamp: Long,
  properties: Map[String, Any],
  log : Logger
) {

  val trimmedFileName : String = fileName.trim()

  override def equals(obj: Any): Boolean = obj match {
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

  override def hashCode(): Int = toString().hashCode()

  val timestampAsString : String = FileDropCommand.tsPattern.format(new Date(timestamp))

  override def toString: String = {

    s"FileDropCommand[$id](dir = [$directory], fileName = [$fileName], compressed = $compressed, append = $append, " +
    s"timestamp = [$timestampAsString], content-size = ${content.length}), properties=${properties.mkString("[", ",", "]")}"
  }

  // determine the final file name for a file drop
  val finalFile : File = {

    if (trimmedFileName.length() < fileName.length()) {
      log.warn(s"Using trimmed file name [$trimmedFileName] for [${toString()}]")
    }

    val file = new File(directory, trimmedFileName)

    if (!append) {
      if (file.exists()) {
        // In case we need to generate a new file name
        new File(directory, fileName.lastIndexOf('.') match {
          case -1 => s"dup_${timestampAsString}_${fileName}"
          case pos => s"${fileName.substring(0, pos)}.dup_${timestampAsString}${fileName.substring(pos)}"
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
}

object FileDropResult {
  def result(cmd: FileDropCommand, error: Option[Throwable]): FileDropResult = new FileDropResult(
    cmd.copy(content = ByteString("")), error
  )
}

case class FileDropResult(cmd: FileDropCommand, error: Option[Throwable])
case object FileDropChunk
case class FileDropAbort(id: String, t:Throwable)

class FileDropActor extends Actor {

  private val actorLog : Logger = Logger[FileDropActor]

  /**
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
    os : Option[OutputStream],
    error : Option[Throwable]
  )

  // scalastyle:off magic.number
  private val buffer : Array[Byte] = new Array[Byte](4096)
  // scalastyle:on magic.number

  override def preStart(): Unit = context.become(idle(Seq.empty))

  private def checkDirectory(dir: File) : Try[File] = {

    if (!dir.exists()) {
      actorLog.debug(s"Creating directory [${dir.getAbsolutePath}]")
      dir.mkdirs()
    }

    if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
      Success(dir)
    } else {
      Failure(new Exception(s"Directory [${dir.getAbsolutePath()}] does not exist or is not writable."))
    }
  }

  // A temp file is only created when we need to append to an existing file.
  def tmpFile(cmd: FileDropCommand) : Try[Option[File]] = Try {

    val filter : DirectoryStream.Filter[Path] = new DirectoryStream.Filter[Path] {
      override def accept(entry: Path): Boolean = {
        val n : String = entry.toFile().getName()
        val p : String = cmd.finalFile.getName()
        n.startsWith(p) && n.endsWith(".tmp")
      }
    }

    def sourceFile(f : File) : Option[File] = {
      if (f.exists()) {
        Some(f)
      } else {
        val dirStream = Files.newDirectoryStream(new File(cmd.directory).toPath(), filter)
        val files : List[File] = dirStream.iterator().asScala.toList.map(_.toFile()).sortBy(_.lastModified()).reverse
        if (files.isEmpty) {
          None
        } else {
          val result : File = files.head
          cmd.log.warn(s"Using tmp file for append source in [${cmd.id}] : [${result.getAbsolutePath()}]")
          files.tail.foreach{ f =>
            cmd.log.debug(s"Removing tmp file [$f]")
            f.delete()
          }
          Some(result)
        }
      }
    }

    if (cmd.append) {
      sourceFile(cmd.finalFile) match {
        case Some(f) =>
          val tmpName = s"${cmd.fileName}.${cmd.timestampAsString}.tmp"
          val tmpFile = new File(cmd.directory, tmpName)
          cmd.log.debug(s"Creating temporary file for [${cmd.id}] [${tmpFile.getAbsolutePath}]")
          Files.move(f.toPath(), tmpFile.toPath(), REPLACE_EXISTING)
          Some(tmpFile)
        case None =>
          None
      }
    } else {
      None
    }
  }

  // The outfile is the file that will temporarily hold the final content
  def outFile(cmd: FileDropCommand) : File =
    new File(cmd.directory, s"${cmd.fileName}.${cmd.timestampAsString}.out")

  // prepare the output stream, if required for append
  def prepareOutputStream(cmd: FileDropCommand) : Try[(OutputStream, Option[File], File)] = Try {

    val tf : Option[File] = tmpFile(cmd).get

    val of : File = outFile(cmd)

    val outStream = if (!cmd.append) {
      new FileOutputStream(of)
    } else {
      tf match {
        case None => new FileOutputStream(of)
        case Some(f) =>
          Files.copy(f.toPath, of.toPath)
          cmd.log.debug(s"Appending to file for [${cmd.id}] [${of.getAbsolutePath()}]")
          new FileOutputStream(of, true)
      }
    }

    (outStream, tf, of)
  }

  // Create the input stream, potentially from a zip stream
  def inputStream(cmd : FileDropCommand) : Option[InputStream] = {
    if (cmd.compressed) {

      try {
        Some(new GZIPInputStream(new BufferedInputStream(new ByteArrayInputStream(cmd.content.toArray))))
      } catch {
        case NonFatal(_) =>
          val zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(cmd.content.toArray)))
          val next = Option(zis.getNextEntry)
          if (next.isEmpty) None else Some(zis)
      }
    } else {
      Some(new ByteArrayInputStream(cmd.content.toArray))
    }
  }

  private def cleanUp(state : FileDropState) : FileDropState = {

    val newState : FileDropState = {
      try {
        state.is.foreach(_.close())
        state.os.foreach(_.close())

        if (state.error.isEmpty) {
          state.cmd.log.debug(s"Removing tmp file for [${state.cmd.id}] : [${state.tmpFile}]")
          state.tmpFile.foreach{ tf => Files.delete(tf.toPath()) }
          state.cmd.log.debug(s"Creating final file for [${state.cmd.id}] : [${state.cmd.finalFile}]")
          Files.move(state.outFile.toPath(), state.cmd.finalFile.toPath(), REPLACE_EXISTING)
        }

        state
      } catch {
        case NonFatal(t) =>
          state.cmd.log.warn(t)(s"Error creating final file for [${state.cmd}]")
          state.copy(error = Some(t))
      }
    }

    if (newState.error.isDefined) {
      // in case an error was encountered, we will restore the original file
      // and forget the append
      state.tmpFile.foreach{ tf =>
        try {
          Files.move(tf.toPath(), state.cmd.finalFile.toPath(), REPLACE_EXISTING)
        } catch {
          case NonFatal(t) =>
            newState.cmd.log.warn(t)(s"Error cleaning up files (move) for [${state.cmd}]")
        }

        try {
          Files.delete(state.outFile.toPath())
        } catch {
          case NonFatal(t) =>
            newState.cmd.log.warn(t)(s"Error cleaning up files (delete) for [${state.cmd}]")
        }
      }
    } else {
      // In case the command was successful, we will delete the tmpfile
      // and create the final file
      newState.cmd.log.info(
        s"Successfully processed filedrop [${state.cmd}] and created file [${state.cmd.finalFile.getAbsolutePath()}]"
      )
    }

    newState
  }

  private[this] def respond(
    state : FileDropState,
    pending : Seq[FileDropState]
  ) : Unit = {

    val result : FileDropState = cleanUp(state)
    state.requestor ! FileDropResult.result(result.cmd, result.error)

    pending match {
      case Seq() =>
        actorLog.debug(s"No more pending Filedrops...switching to idle state")
        context.become(idle(Seq.empty))
      case s =>
        val state : FileDropState = s.last
        actorLog.debug(s"Scheduling pending file drop [${state.cmd.id}]")
        context.become(dropping(state, s.take(s.size - 1)))
        self ! FileDropChunk
    }
  }

  override def receive: Receive = Actor.emptyBehavior

  // While in dropping state we drop one chunk after the other
  private def dropping(state : FileDropState, pending : Seq[FileDropState]) : Receive = {
    case cmd : FileDropCommand =>
      val newState : FileDropState = createDropState(cmd, sender())
      cmd.log.debug(s"File drop for [${cmd.id}] is pending, [${pending.size + 1}] pending in total")
      context.become(dropping(state, pending ++ Seq(newState)))
      self ! FileDropChunk

    // Abort the file drop
    case FileDropAbort(id, t) =>
      if (state.cmd.id == id) {
        respond(state.copy(error = Some(t)), pending)
      } else {
        pending.find(_.cmd.id == id).foreach{ s =>
          cleanUp(s)
          s.requestor ! FileDropResult.result(s.cmd, Some(t))
          context.become(dropping(state, pending.filter(_.cmd.id != id)))
        }
      }

    // Drop the next chunk to the out directory
    case FileDropChunk =>
      (state.is, state.os) match {
        // Streams are still open, so we can proceed
        case (Some(in), Some(out)) =>
          try {
            val cnt = in.read(buffer)
            if (cnt >= 0) {
              out.write(buffer, 0, cnt)
              self ! FileDropChunk
            } else {
              state.cmd.log.debug(s"Successfully wrote message [${state.cmd.id}] to [${state.outFile}].")
              respond(state.copy(error = None), pending)
            }
          } catch {
            case NonFatal(e) =>
              state.cmd.log.warn(e)(s"Error while dropping file [${state.cmd}] : [${e.getMessage()}]")
              respond(state. copy(error = Some(e)), pending)
          }
        // The input or output stream is not open
        case (_,_) =>
          respond(state.copy(error = Some(new Exception("Illegal stream state in FileDropActor"))), pending)
      }
  }

  private def createDropState(cmd : FileDropCommand, requestor: ActorRef) : FileDropState = {
    checkDirectory(new File(cmd.directory)) match {
      case Success(_) => prepareOutputStream(cmd) match {
        case Success((s, tf, of)) =>
          FileDropState(requestor = requestor, cmd = cmd, tmpFile = tf,
            outFile = of, is = inputStream(cmd), os = Some(s), error = None
          )
        case Failure(t) =>
          FileDropState(requestor = requestor, cmd = cmd, tmpFile = None,
            outFile = outFile(cmd), is = None, os = None, error = Some(t)
          )
      }

      case Failure(t) =>
        FileDropState(requestor = requestor, cmd = cmd, tmpFile = None,
          outFile = outFile(cmd), is = None, os = None, error = Some(t)
        )
    }
  }

  private def idle(pending : Seq[FileDropState]) : Receive = {

    case FileDropChunk =>
    case FileDropAbort(_, _) =>

    case cmd : FileDropCommand =>
      val state : FileDropState = createDropState(cmd, sender())
      if(state.error.isEmpty) {
        context.become(dropping(state, pending))
        self ! FileDropChunk
      } else {
        respond(state, pending)
      }
  }
}
