package blended.file

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

sealed abstract class FileCommand()
case class DeleteFile(f: File) extends FileCommand
case class RenameFile(src: File, dest: File) extends FileCommand
case class FileCmdResult(cmd: FileCommand, t : Option[Throwable])

object FileManipulationActor {

  val operationTimeout : FiniteDuration = {
    val config = ConfigFactory.load()
    val toPath = "blended.file.operationTimeout"
    (if (config.hasPath(toPath)) config.getLong(toPath) else 100l).millis
  }
}

class FileCommandTimeoutException(cmd : FileCommand) extends Exception(s"Command [$cmd] timed out")

class FileManipulationActor extends Actor {

  private val log : Logger = Logger[FileManipulationActor]

  case object Tick
  case object Timeout

  implicit val eCtxt : ExecutionContext  = context.system.dispatcher

  private[this] def executeCmd(cmd: FileCommand) : Boolean = {
    cmd match {
      case DeleteFile(f) =>
        f.delete()
        if (f.exists()) {
          log.trace(s"Attempt to delete file [${f.getAbsolutePath}] failed.")
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
            log.trace(s"Attempt to rename file [${src.getAbsolutePath}] to [${dest.getAbsolutePath}] failed.")
            false
          } else {
            true
          }
        }
    }
  }

  override def receive: Receive = {
    case cmd : FileCommand =>
      context.become(executing(sender(), cmd, List(context.system.scheduler.scheduleOnce(FileManipulationActor.operationTimeout, self, Timeout))))
      self ! Tick
  }

  def stop(t: List[Cancellable]) : Unit = {
    t.foreach(_.cancel())
    context.stop(self)
  }

  def executing(requestor: ActorRef, cmd: FileCommand, t: List[Cancellable]): Receive = {
    case Tick =>
      if (executeCmd(cmd)) {
        log.info(s"File command [$cmd] succeeded.")
        requestor ! FileCmdResult(cmd, None)
        stop(t)
      } else {
        context.become(executing(requestor, cmd, context.system.scheduler.scheduleOnce(10.millis, self, Tick) :: t.tail))
      }

    case Timeout =>
      requestor ! FileCmdResult(cmd, Some(new FileCommandTimeoutException(cmd)) )
      stop(t)
  }
}
