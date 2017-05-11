package blended.file

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}

import scala.concurrent.duration._

sealed abstract class FileCommand()
case class DeleteFile(f: File) extends FileCommand
case class RenameFile(src: File, dest: File) extends FileCommand
case class FileCmdResult(cmd: FileCommand, success: Boolean)

object FileManipulationActor {

  def props(requestor: ActorRef, cmd: FileCommand) : Props = {
    Props(new FileManipulationActor(requestor, cmd))
  }
}

class FileManipulationActor(requestor: ActorRef, cmd: FileCommand) extends Actor with ActorLogging {

  case object Tick
  case object Timeout

  val config = context.system.settings.config
  implicit val eCtxt = context.system.dispatcher

  override def preStart(): Unit = {

    val toPath = "blended.file.operationTimeout"

    val maxWait = (if (config.hasPath(toPath)) config.getLong(toPath) else 5000l).millis
    context.become(waiting(List(context.system.scheduler.scheduleOnce(maxWait, self, Timeout))))
    self ! Tick
  }

  private[this] def executeCmd(cmd: FileCommand) : Boolean = {
    cmd match {
      case DeleteFile(f) =>
        f.delete()
        if (f.exists()) {
          log.info(s"Attempt to delete file [${f.getAbsolutePath()}] failed.")
          false
        } else {
          true
        }
      case RenameFile(src, dest) =>
        if (dest.exists()) {
          false
        } else {
          src.renameTo(dest)
          if (!dest.exists() || src.exists()) {
            false
          } else {
            true
          }
        }
    }
  }

  override def receive: Receive = Actor.emptyBehavior

  def stop(t: List[Cancellable]) = {
    t.foreach(_.cancel())
    context.stop(self)
  }

  def waiting(t: List[Cancellable]): Receive = {
    case Tick =>
      executeCmd(cmd) match {
        case true =>
          log.debug(s"File command [$cmd] succeeded.")
          requestor ! FileCmdResult(cmd, true)
          stop(t)
        case false =>
          context.become(waiting(context.system.scheduler.scheduleOnce(10.millis, self, Tick) :: t.tail))
      }

    case Timeout =>
      log.info(s"Command [$cmd] timed out")
      requestor ! FileCmdResult(cmd, false)
      stop(t)
  }
}
