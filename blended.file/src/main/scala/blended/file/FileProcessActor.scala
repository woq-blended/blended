package blended.file

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout

import scala.util.control.NonFatal

case class FileProcessCmd(f: File, cfg: FilePollConfig, handler: FilePollHandler)
case class FileProcessed(cmd: FileProcessCmd, success: Boolean)

class FileProcessActor extends Actor with ActorLogging {

  implicit val timeout = Timeout(FileManipulationActor.operationTimeout)
  implicit val eCtxt = context.system.dispatcher

  override def receive: Receive = {
    case cmd : FileProcessCmd =>
      val tempFile = new File(cmd.f.getParentFile(), cmd.f.getName() + cmd.cfg.tmpExt)
      context.actorOf(Props[FileManipulationActor]).tell(RenameFile(cmd.f, tempFile), self)
      context.become(initiated(sender(), tempFile, cmd))
  }

  def initiated(requestor: ActorRef, tempFile: File, cmd: FileProcessCmd) : Receive = {
    case result : FileCmdResult => result.success match {
      case false => log.warning(s"File [${cmd.f.getAbsolutePath()}] can't be accessed yet - processing delayed.")
      case true =>
        try {
          cmd.handler.processFile(cmd, tempFile)
          requestor ! FileProcessed(cmd, true)

          val archiveCmd = cmd.cfg.backup match {
            case None =>
              DeleteFile(tempFile)
            case Some(d) =>
              RenameFile(tempFile, new File(d, cmd.f.getName()))
          }

          context.actorOf(Props[FileManipulationActor]).tell(archiveCmd, self)
          context.become(cleanUp(requestor, cmd, true))
        } catch {
          case NonFatal(t) => {
            context.actorOf(Props[FileManipulationActor]).tell(RenameFile(tempFile, cmd.f), self)
            context.become(cleanUp(requestor, cmd, false))
          }
        }
    }
  }

  def cleanUp(requestor: ActorRef, cmd: FileProcessCmd, success: Boolean) : Receive = {
    case result : FileCmdResult =>
      val result = FileProcessed(cmd, success)
      context.system.eventStream.publish(result)
      requestor ! result

      context.stop(self)
  }
}
