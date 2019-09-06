package blended.file

import java.io._
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{GZIPInputStream, ZipInputStream}

import akka.actor.{Actor, ActorRef}
import akka.util.ByteString
import blended.util.logging.Logger

import scala.util.{Failure, Success, Try}
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

  override def equals(obj: Any): Boolean = obj match {
    case cmd : FileDropCommand =>
      content.equals(cmd.content) &&
      directory.equals(cmd.directory) &&
      fileName.equals(cmd.fileName) &&
      compressed == cmd.compressed &&
      append == cmd.append &&
      timestamp == cmd.timestamp &&
      properties.equals(cmd.properties)
    case _ => false
  }

  override def hashCode(): Int = toString().hashCode()

  override def toString: String = {

    val ts = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS").format(new Date(timestamp))
    s"FileDropCommand[$id](dir = [$directory], fileName = [$fileName], compressed = $compressed, append = $append, " +
    s"timestamp = [$ts], content-size = ${content.length}), properties=${properties.mkString("[", ",", "]")}"
  }

  // determine the final file name for a file drop
  val finalFile : File = {

    val file = new File(directory, fileName)

    if (!append) {
      if (file.exists()) {
        // In case we need to generate a new file name
        new File(directory, fileName.lastIndexOf('.') match {
          case -1 => s"dup_${System.currentTimeMillis()}_${fileName}"
          case pos => s"${fileName.substring(0, pos)}.dup_${System.currentTimeMillis()}${fileName.substring(pos)}"
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

class FileDropController extends Actor {
  override def receive: Receive = Actor.emptyBehavior
}

class FileDropActor extends Actor {

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

  private val log : Logger = Logger[FileDropActor]

  override def preStart(): Unit = context.become(idle(Seq.empty))

  private def checkDirectory(dir: File) : Try[File] = {

    if (!dir.exists()) {
      log.debug(s"Creating directory [${dir.getAbsolutePath}]")
      dir.mkdirs()
    }

    if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
      Success(dir)
    } else {
      Failure(new Exception(s"Directory [${dir.getAbsolutePath()}] does not exist or is not writable."))
    }
  }

  // A temp file is only created when we need to append to an existing file.
  def tmpFile(cmd: FileDropCommand) : Option[File] = {
    if (cmd.append) {
      val file : File = cmd.finalFile
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
  def prepareOutputStream(cmd: FileDropCommand) : Try[(OutputStream, Option[File], File)] = Try {

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
      log.debug(s"Writing content for cmd [${cmd.id}] without compression to [${outFile(cmd)}]")
      Some(new ByteArrayInputStream(cmd.content.toArray))
    }
  }

  private def cleanUp(state : FileDropState) : Unit = {
    Try(state.is.foreach(_.close()))
    Try(state.os.foreach(_.close()))

    if (state.error.isDefined) {
      // in case an error was encountered, we will restore the original file
      // and forget the append
      state.tmpFile.foreach { tf => tf.renameTo(state.cmd.finalFile) }
      state.outFile.delete()
    } else {
      // In case the command was successful, we will delete the tmpfile
      // and create the final file
      state.tmpFile.foreach(_.delete())
      state.outFile.renameTo(state.cmd.finalFile)
    }
  }

  private[this] def respond(
    state : FileDropState,
    pending : Seq[FileDropState]
  ) : Unit = {

    cleanUp(state)
    state.requestor ! FileDropResult.result(state.cmd, state.error)

    pending match {
      case Seq() =>
        log.debug(s"No more pending Filedrops...switching to idle state")
        context.become(idle(Seq.empty))
      case s =>
        val state : FileDropState = s.last
        log.debug(s"Scheduling pending file drop [${state.cmd.id}]")
        context.become(dropping(state, s.take(s.size - 1)))
        self ! FileDropChunk
    }
  }

  override def receive: Receive = Actor.emptyBehavior

  // While in dropping state we drop one chunk after the other
  private def dropping(state : FileDropState, pending : Seq[FileDropState]) : Receive = {
    case cmd : FileDropCommand =>
      val newState : FileDropState = createDropState(cmd, sender())
      log.debug(s"File drop for [${cmd.id}] is pending, [${pending.size + 1}] pending in total")
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
      log.trace("Dropping chunk")
      (state.is, state.os) match {
        // Streams are still open, so we can proceed
        case (Some(in), Some(out)) =>
          try {
            // scalastyle:off magic.number
            val buffer : Array[Byte] = new Array[Byte](4096)
            // scalastyle:on magic.number
            val cnt = in.read(buffer)
            if (cnt >= 0) {
              out.write(buffer, 0, cnt)
              self ! FileDropChunk
            } else {
              log.info(s"Successfully executed [${state.cmd}] and created file [${state.cmd.finalFile.getAbsolutePath}]")
              respond(state.copy(error = None), pending)
            }
          } catch {
            case NonFatal(e) =>
              log.warn(e)(s"Error while dropping file [${e.getMessage()}]")
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
