package blended.file

import java.io.File
import java.text.SimpleDateFormat
import java.util.{Date, UUID}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import akka.util.Timeout

import scala.concurrent.ExecutionContext

case class FileProcessCmd(
  originalFile : File,
  workFile : Option[File] = None,
  cfg: FilePollConfig,
  handler: FilePollHandler
) {
  val fileToProcess : File = workFile.getOrElse(originalFile)
  val id : String = UUID.randomUUID().toString()
}

case class FileProcessed(cmd: FileProcessCmd, success: Boolean)

class FileProcessActor extends Actor with ActorLogging {

  implicit val timeout : Timeout = Timeout(FileManipulationActor.operationTimeout)
  implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private[this] val sdf = new SimpleDateFormat("yyyyMMdd-HHmmssSSS")

  override def receive: Receive = {
    case cmd : FileProcessCmd =>
      val tempFile : File = new File(cmd.originalFile.getParentFile, cmd.originalFile.getName + cmd.cfg.tmpExt)
      context.actorOf(Props[FileManipulationActor]).tell(RenameFile(cmd.originalFile, tempFile), self)
      context.become(initiated(sender(), cmd.copy(workFile = Some(tempFile))))
  }

  def initiated(requestor: ActorRef, cmd: FileProcessCmd) : Receive = {

    case result : FileCmdResult if result.success =>
      cmd.handler.processFile(cmd, cmd.fileToProcess)(context.system).pipeTo(self)

    case result : FileCmdResult if !result.success =>
      log.warning(s"File [${cmd.originalFile.getAbsolutePath}] can't be accessed yet - processing delayed.")
      context.become(cleanUp(requestor, cmd, success = false))
      self.forward(result)

    case p : (FileProcessCmd, Option[Throwable]) if p._2.isEmpty =>
      requestor ! FileProcessed(cmd, success = true)

      val archiveCmd = cmd.cfg.backup match {
        case None =>
          DeleteFile(cmd.fileToProcess)
        case Some(d) =>
          val backupDir = new File(d)
          if (!backupDir.exists()) {
            backupDir.mkdirs()
          }
          val backupFileName = cmd.originalFile.getName + "-" + sdf.format(new Date())
          RenameFile(cmd.fileToProcess, new File(d, backupFileName))
      }

      context.actorOf(Props[FileManipulationActor]).tell(archiveCmd, self)
      context.become(cleanUp(requestor, cmd, success = true))

    case p : (FileProcessCmd, Option[Throwable]) if p._2.isDefined =>
      val t : Throwable = p._2.get
      log.warning(s"Failed to process file [${p._1.originalFile.getAbsolutePath()}] : [${t.getMessage()}]")
      context.actorOf(Props[FileManipulationActor]).tell(RenameFile(cmd.fileToProcess, cmd.originalFile), self)
      context.become(cleanUp(requestor, cmd, success = false))
  }

  def cleanUp(requestor: ActorRef, cmd: FileProcessCmd, success: Boolean) : Receive = {
    case _ : FileCmdResult =>
      val result = FileProcessed(cmd, success)
      context.system.eventStream.publish(result)
      requestor ! result

      context.stop(self)
  }
}
