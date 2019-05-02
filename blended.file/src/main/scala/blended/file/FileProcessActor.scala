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

case class FileProcessResult(cmd: FileProcessCmd, t : Option[Throwable]) {
  val id = cmd.id
}

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

    case FileCmdResult(_, None) =>
      cmd.handler.processFile(cmd).pipeTo(self)

    case r @ FileCmdResult(_, Some(t)) =>
      log.warning(s"File [${cmd.originalFile.getAbsolutePath}] can't be accessed yet - processing delayed.")
      context.become(cleanUp(requestor, FileProcessResult(cmd, Some(t))))
      self.forward(r)

    case c @ FileProcessResult(command, None) =>
      val archiveCmd = command.cfg.backup match {
        case None =>
          DeleteFile(command.fileToProcess)
        case Some(d) =>
          val backupDir = new File(d)
          if (!backupDir.exists()) {
            backupDir.mkdirs()
          }
          val backupFileName = command.originalFile.getName + "-" + sdf.format(new Date())
          RenameFile(c.cmd.fileToProcess, new File(d, backupFileName))
      }

      context.actorOf(Props[FileManipulationActor]).tell(archiveCmd, self)
      context.become(cleanUp(requestor, c))

      self ! c

    case r @ FileProcessResult(_, Some(t)) =>
      log.warning(s"Failed to process file [${cmd.originalFile.getAbsolutePath()}] : [${t.getMessage()}]")
      context.actorOf(Props[FileManipulationActor]).tell(RenameFile(cmd.fileToProcess, cmd.originalFile), self)
      context.become(cleanUp(requestor, r))

    case m => println("xxx" + m.toString)
  }

  def cleanUp(requestor: ActorRef, r: FileProcessResult) : Receive = {
    case _ : FileCmdResult =>
      context.system.eventStream.publish(r)
      requestor ! r

      context.stop(self)
  }
}
