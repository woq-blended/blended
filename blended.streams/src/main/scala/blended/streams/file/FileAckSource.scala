package blended.streams.file

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{DirectoryStream, Files, Path}
import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.ActorSystem
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Outlet, SourceShape}
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope, FlowMessage}
import blended.streams.{AckSourceLogic, DefaultAcknowledgeContext}
import blended.util.FileHelper
import blended.util.logging.Logger

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object FileAckSource {

  private val lockedDirs : mutable.ListBuffer[String] = mutable.ListBuffer.empty

  def lockDirectory(dir : String) : Boolean = lockedDirs.synchronized {
    if (!lockedDirs.contains(dir)) {
      lockedDirs += dir
      true
    } else {
      false
    }
  }

  def releaseDirectory(dir : String) : Unit = lockedDirs.synchronized {
    lockedDirs -= dir
  }
}

class FileAckSource(
  pollCfg : FilePollConfig
)(implicit system : ActorSystem) extends GraphStage[SourceShape[FlowEnvelope]] {

  private val pollId : String = s"${pollCfg.headerCfg.prefix}.FilePoller.${pollCfg.id}.source"
  private val out : Outlet[FlowEnvelope] = Outlet(name = pollId)
  private val sdf = new SimpleDateFormat("yyyyMMdd-HHmmssSSS")

  override def shape : SourceShape[FlowEnvelope] = SourceShape(out)

  private class FileAckContext(
    inflightId : String,
    env : FlowEnvelope,
    val originalFile : File,
    val fileToProcess : File
  ) extends DefaultAcknowledgeContext(inflightId, env, System.currentTimeMillis())

  private class FileSourceLogic() extends AckSourceLogic[FileAckContext](out, shape) {
    /** The id to identify the instance in the log files */
    override def id : String = pollId

    private var pendingFiles : mutable.ListBuffer[File] = ListBuffer.empty

    /** A logger that must be defined by concrete implementations */
    override protected def log : Logger = Logger(pollId)

    /** The id's of the available inflight slots */
    override protected def inflightSlots() : List[String] =
      1.to(pollCfg.batchSize).map(i => s"FilePoller-${pollCfg.id}-$i").toList

    // Reset the polling interval
    override protected def nextPoll() : Option[FiniteDuration] = if (pendingFiles.isEmpty) Some(pollCfg.interval) else None

    override protected def doPerformPoll(id : String, ackHandler : AcknowledgeHandler) : Try[Option[FileAckContext]] = {

      def createEnvelope(f : File) : Try[Option[FileAckContext]] = Try {

        val sdf : SimpleDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmssSSS")

        if (f.exists()) {
          val envId : String = s"${sdf.format(new Date())}-${f.getName()}"
          log.info(s"Processing file [$f] in [$id] with [$envId]")

          val fileToProcess : File = new File(f + pollCfg.tmpExt)

          // First we try to rename the file in order to check whether it can be accessed yet
          if (FileHelper.renameFile(f, fileToProcess)) {
            val bytes : Array[Byte] = FileHelper.readFile(fileToProcess.getAbsolutePath())

            val msg : FlowMessage = if (pollCfg.asText) {
              val charSet : Charset = pollCfg.charSet match {
                case None    => Charset.defaultCharset()
                case Some(s) => Charset.forName(s)
              }

              log.debug(s"Using charset [${charSet.displayName()}] to create text message.")

              FlowMessage(new String(bytes, charSet))(pollCfg.header)
            } else {
              FlowMessage(bytes)(pollCfg.header)
            }

            val env : FlowEnvelope = FlowEnvelope(msg, envId)
              .withHeader(pollCfg.filenameProp, f.getName()).get
              .withHeader(pollCfg.filepathProp, f.getAbsolutePath()).get
              .withRequiresAcknowledge(true)
              .withAckHandler(Some(ackHandler))

            log.debug(s"Created Envelope [$env] in [$id]]")

            Some(new FileAckContext(
              inflightId = id,
              env = env,
              originalFile = f,
              fileToProcess = fileToProcess
            ))
          } else {
            None
          }
        } else {
          None
        }
      }

      // first we try to lock the directory if it can't be locked, we will assume that another file poller is currently
      // working on the same directory
      try {
        if (FileAckSource.lockDirectory(pollCfg.sourceDir)) {
          files() match {
            case None    => Success(None)
            case Some(f) => Success(createEnvelope(f).get)
          }
        } else {
          log.debug(s"Directory [${pollCfg.sourceDir}] is currently locked by another file poller.")
          Success(None)
        }
      } catch {
        case NonFatal(t) =>
          log.warn(t)(s"Error polling directory [${pollCfg.sourceDir}] for [$id]")
          Failure(t)
      } finally {
        FileAckSource.releaseDirectory(pollCfg.sourceDir)
      }
    }

    override protected def beforeAcknowledge(ackCtxt : FileAckContext) : Unit = {
      log.info(s"Successfully processed envelope [${ackCtxt.envelope.id}]")
      pollCfg.backup match {
        case None =>
          if (ackCtxt.fileToProcess.delete()) {
            log.info(s"Deleted file for [${ackCtxt.envelope.id}] : [${ackCtxt.fileToProcess}]")
          } else {
            log.warn(s"File for [${ackCtxt.envelope.id}] could not be deleted : [${ackCtxt.fileToProcess}]")
          }
        case Some(d) =>

          val backupDir = new File(d)
          if (!backupDir.exists()) {
            backupDir.mkdirs()
          }

          val backupFileName = ackCtxt.originalFile.getName + "-" + sdf.format(new Date())

          val fFrom : File = ackCtxt.fileToProcess
          val fTo : File = new File(backupDir, backupFileName)

          if (FileHelper.renameFile(fFrom, fTo)) {
            log.info(s"Moved file for [${ackCtxt.envelope.id}] from [${fFrom.getAbsolutePath()}] to [${fTo.getAbsolutePath()}]")
          } else {
            log.warn(s"File for [${ackCtxt.envelope.id}] failed to be renamed from [${fFrom.getAbsolutePath()}] to [${fTo.getAbsolutePath()}]")
          }
      }
    }

    override protected def beforeDenied(ackCtxt : FileAckContext) : Unit = {
      log.info(s"Restoring file [${ackCtxt.originalFile}] in [${ackCtxt.inflightId}]")
      FileHelper.renameFile(ackCtxt.fileToProcess, ackCtxt.originalFile)
    }

    /**
     * The file poller can be locked by the existence of a lock file if specified.
     */
    private[this] def locked() : Boolean = pollCfg.lock match {
      case None => false
      case Some(l) =>
        val f = if (l.startsWith("./")) {
          new File(pollCfg.sourceDir, l.substring(2))
        } else {
          new File(l)
        }

        if (f.exists()) {
          log.info(s"Directory for [${pollCfg.id}] is locked with file [${f.getAbsolutePath()}]")
          true
        } else {
          false
        }
    }

    /**
     * Here we will poll for the next file to be processed by the file poller.
     * If the directory is not locked by a lock file or another instance polling the
     * same directory, we will scan the directory for the file to be processed and
     * return the handle to the first file found.
     *
     * If the dir is locked or no file is found, we will return [[None]]
     */
    protected def files() : Option[File] = {
      val srcDir = new File(pollCfg.sourceDir)

      // First we make sure the poll directory exists
      if (!srcDir.exists()) {
        srcDir.mkdirs()
      }

      // If the directory does not yet exist or is not readable, we log a warning
      if (!srcDir.exists() || !srcDir.isDirectory() || !srcDir.canRead()) {
        log.warn(s"Directory [$srcDir] for [${pollCfg.id}] does not exist or is not readable.")
        None
      } else if (locked()) {
        None
      } else {

        if (pendingFiles.isEmpty) {
          log.info(s"Executing directory scan for [${pollCfg.id}] for directory [${pollCfg.sourceDir}] with pattern [${pollCfg.pattern}]")

          try {
            val filter : DirectoryStream.Filter[Path] = new DirectoryStream.Filter[Path] {
              override def accept(entry : Path) : Boolean = {
                entry.getParent().toFile().equals(srcDir) && pollCfg.pattern.forall(p => entry.toFile().getName().matches(p))
              }
            }

            val dirStream : DirectoryStream[Path] = Files.newDirectoryStream(srcDir.toPath(), filter)

            try {
              dirStream.iterator().asScala.take(100).map(_.toFile()).foreach(f => pendingFiles += f)
            } catch {
              case NonFatal(e) =>
                log.warn(s"Error reading directory [${srcDir.getAbsolutePath()}] : [${e.getMessage()}]")
                None
            } finally {
              dirStream.close()
            }
          }
        }

        val result = pendingFiles.headOption
        pendingFiles = if (!pendingFiles.isEmpty) pendingFiles.tail else pendingFiles

        result
      }
    }
  }

  override def createLogic(inheritedAttributes : Attributes) : GraphStageLogic =
    new FileSourceLogic()
}
