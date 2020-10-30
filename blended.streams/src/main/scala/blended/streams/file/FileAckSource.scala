package blended.streams.file

import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.{Date, UUID}

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Outlet, SourceShape}
import blended.streams.message.{AcknowledgeHandler, FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.streams.{AckSourceLogic, DefaultAcknowledgeContext}
import blended.util.FileHelper
import blended.util.logging.LogLevel

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

object FileAckSource {

  private val sources : mutable.Map[String, DirectorySource] = mutable.Map.empty

  def nextFile(pollCfg : FilePollConfig) : Option[File] = {

    val key : String = s"${pollCfg.sourceDir}-${pollCfg.pattern}"

    val dirSource : DirectorySource = sources.synchronized {
      sources.get(key) match {
        case None =>
          val src: DirectorySource = new DirectorySource(pollCfg)
          sources += (key -> src)
          src
        case Some(s) => s
      }
    }

    dirSource.synchronized {
      dirSource.nextFile()
    }
  }
}

class FileAckSource(
  pollCfg : FilePollConfig,
  logger : FlowEnvelopeLogger
) extends GraphStage[SourceShape[FlowEnvelope]] {

  private val pollId : String =  s"${pollCfg.headerCfg.prefix}.FilePoller.${pollCfg.id}.source"
  private val out : Outlet[FlowEnvelope] = Outlet(name = pollId)
  private val sdf = new SimpleDateFormat("yyyyMMdd-HHmmssSSS")

  override def shape: SourceShape[FlowEnvelope] = SourceShape(out)

  private class FileAckContext(
    inflightId : String,
    env: FlowEnvelope,
    val originalFile : File,
    val fileToProcess : File
  ) extends DefaultAcknowledgeContext(inflightId, env, pollCfg.ackTimeout, System.currentTimeMillis()) {

    override def acknowledge() : Unit = {
      logger.logEnv(env, LogLevel.Debug, s"Successfully processed envelope [${envelope.id}]")
      pollCfg.backup match {
        case None =>
          if (fileToProcess.delete()) {
            logger.logEnv(env, LogLevel.Debug, s"Deleted file for [${envelope.id}] : [${fileToProcess}]")
          } else {
            logger.logEnv(env, LogLevel.Warn, s"File for [${envelope.id}] could not be deleted : [${fileToProcess}]")
          }
        case Some(d) =>

          val backupDir = new File(d)
          if (!backupDir.exists()) {
            backupDir.mkdirs()
          }

          val backupFileName =
            if (pollCfg.backupTimestamp) {
              originalFile.getName + "-" + sdf.format(new Date())
            } else {
              originalFile.getName
            }

          val fFrom : File = fileToProcess
          val fTo : File = new File(backupDir, backupFileName)

          if (FileHelper.renameFile(fFrom, fTo, true)) {
            logger.logEnv(env, LogLevel.Debug, s"Moved file for [${envelope.id}] from [${fFrom.getAbsolutePath()}] to [${fTo.getAbsolutePath()}]")
          } else {
            logger.logEnv(env, LogLevel.Debug, s"File for [${envelope.id}] failed to be renamed from [${fFrom.getAbsolutePath()}] to [${fTo.getAbsolutePath()}]")
          }
      }
    }

    override def deny() : Unit = {
      logger.logEnv(env, LogLevel.Debug, s"Restoring file [${originalFile}] in [${inflightId}]")
      FileHelper.renameFile(fileToProcess, originalFile, true)
    }
  }

  private class FileSourceLogic() extends AckSourceLogic[FileAckContext](shape, out, pollCfg.ackTimeout) {
    /** The id to identify the instance in the log files */
    override val id : String = pollId

    /** A logger that must be defined by concrete implementations */
    override protected def log: FlowEnvelopeLogger = logger

    log.underlying.debug(s"Initializing FileAckSource with config [$pollCfg], ackTimeout [$ackTimeout]")

    /** The id's of the available inflight slots */
    override protected val inflightSlots : List[String] =
      1.to(pollCfg.batchSize).map(i => s"FilePoller-${pollCfg.id}-$i").toList

    override protected def nextPoll(): Option[FiniteDuration] = Some(pollCfg.interval)

    override protected def doPerformPoll(id : String, ackHandler : AcknowledgeHandler) : Try[Option[FileAckContext]] = {

      def createEnvelope(f : File) : Try[Option[FileAckContext]] = Try {

        if (f.exists()) {
          val uuid : String = UUID.randomUUID().toString() + "-" + f.getName()

          val fileToProcess : File = new File(f.getPath() + pollCfg.tmpExt)

          // First we try to rename the file in order to check whether it can be accessed yet
          if (FileHelper.renameFile(f, fileToProcess, false)) {
            val bytes : Array[Byte] = FileHelper.readFile(fileToProcess.getAbsolutePath())

            val msg : FlowMessage = if (pollCfg.asText) {
              val charSet : Charset = pollCfg.charSet match {
                case None => Charset.defaultCharset()
                case Some(s) => Charset.forName(s)
              }

              FlowMessage(new String(bytes, charSet))(pollCfg.header)
            } else {
              FlowMessage(bytes)(pollCfg.header)
            }

            val env : FlowEnvelope = FlowEnvelope(msg,uuid)
              .withHeader(pollCfg.filenameProp, f.getName()).get
              .withHeader(pollCfg.filepathProp, f.getAbsolutePath()).get
              .withRequiresAcknowledge(true)
              .withAckHandler(Some(ackHandler))

            log.logEnv(env, LogLevel.Info, s"Created Envelope [$env] in [$id]]")

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
      if (locked()) {
        Success(None)
      } else {
        FileAckSource.nextFile(pollCfg) match {
          case Some(f) => Success(createEnvelope(f).get)
          case None => Success(None)
        }
      }
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
          log.underlying.debug(s"Directory for [${pollCfg.id}] is locked with file [${f.getAbsolutePath()}]")
          true
        } else {
          false
        }
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new FileSourceLogic()
}
