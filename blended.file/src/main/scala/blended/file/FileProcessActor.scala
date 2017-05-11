package blended.file

import java.io.{File, FileInputStream, InputStream}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout

import scala.util.control.NonFatal

class FileProcessActor extends Actor with ActorLogging {

  case class FileProcessCmd(f: File, cfg: FilePollConfig, handler: FilePollHandler)
  case class FileProcessed(f: File, success: Boolean)

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
      case false => log.warning(s"File [${cmd.f.getAbsolutePath()}] can#t be accessed yet - processing delayed.")
      case true =>
        var is : Option[InputStream] = None
        try {
          is = Some(new FileInputStream(tempFile))
          is match {
            case None =>
              throw new Exception(s"Error opening file [${tempFile}]")
            case Some(s) =>
              cmd.handler.processFile(s, cmd.cfg.header)
              requestor ! FileProcessed(cmd.f, true)
          }
        } catch {
          case NonFatal(t) => {
            context.actorOf(Props[FileManipulationActor]).tell(RenameFile(tempFile, cmd.f), self)
            context.become(failed(requestor, cmd))
          }
        } finally {
          is.foreach(_.close())
        }
    }
  }

  def failed(requestor: ActorRef, cmd: FileProcessCmd) : Receive = {
    case result : FileCmdResult =>
      requestor ! FileProcessed(cmd.f, false)
  }
}
