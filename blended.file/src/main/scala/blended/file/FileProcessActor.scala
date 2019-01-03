package blended.file

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class FileProcessCmd(f: File, cfg: FilePollConfig, handler: FilePollHandler)
case class FileProcessed(cmd: FileProcessCmd, success: Boolean)

class FileProcessActor extends Actor with ActorLogging {

  implicit val timeout : Timeout = Timeout(FileManipulationActor.operationTimeout)
  implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private[this] val sdf = new SimpleDateFormat("yyyyMMdd-HHmmssSSS")

  override def receive: Receive = {
    case cmd : FileProcessCmd =>
      val tempFile = new File(cmd.f.getParentFile, cmd.f.getName + cmd.cfg.tmpExt)
      context.actorOf(Props[FileManipulationActor]).tell(RenameFile(cmd.f, tempFile), self)
      context.become(initiated(sender(), tempFile, cmd))
  }

  def initiated(requestor: ActorRef, tempFile: File, cmd: FileProcessCmd) : Receive = {
    case result : FileCmdResult => if (result.success) {

      cmd.handler.processFile(cmd, tempFile)(context.system) match {
        case Success(_) =>
          requestor ! FileProcessed(cmd, success = true)

          val archiveCmd = cmd.cfg.backup match {
            case None =>
              DeleteFile(tempFile)
            case Some(d) =>
              val backupDir = new File(d)
              if (!backupDir.exists()) {
                backupDir.mkdirs()
              }
              val backupFileName = cmd.f.getName + "-" + sdf.format(new Date())
              RenameFile(tempFile, new File(d, backupFileName))
          }

          context.actorOf(Props[FileManipulationActor]).tell(archiveCmd, self)
          context.become(cleanUp(requestor, cmd, success = true))

        case Failure(e) =>
          context.actorOf(Props[FileManipulationActor]).tell(RenameFile(tempFile, cmd.f), self)
          context.become(cleanUp(requestor, cmd, success = false))
      }


    } else {
      log.warning(s"File [${cmd.f.getAbsolutePath}] can't be accessed yet - processing delayed.")
      context.become(cleanUp(requestor, cmd, success = false))
      self.forward(result)
    }
  }

  def cleanUp(requestor: ActorRef, cmd: FileProcessCmd, success: Boolean) : Receive = {
    case _ : FileCmdResult =>
      val result = FileProcessed(cmd, success)
      context.system.eventStream.publish(result)
      requestor ! result

      context.stop(self)
  }
}
