package blended.file

import java.io.{File, FilenameFilter}
import java.nio.file.{DirectoryStream, Files, Path}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{ask, pipe}
import blended.akka.SemaphoreActor.{Acquire, Acquired, Release, Waiting}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

object FilePollActor {

  val defaultBatchSize : Int = 10

  def props(
    cfg: FilePollConfig,
    handler: FilePollHandler,
    sem : Option[ActorRef] = None,
  ) : Props =
    Props(new FilePollActor(cfg, handler, sem))
}

class FilePollActor(
  cfg: FilePollConfig,
  handler: FilePollHandler,
  sem : Option[ActorRef]
) extends Actor with ActorLogging {

  case object Tick

  private[this] implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private[this] var totalToProcess : Int = 0
  private[this] var pending : List[File] = List.empty

  override def preStart(): Unit = {
    self ! Tick
  }

  private[this] def locked() : Boolean = cfg.lock match {
    case None => false
    case Some(l) =>
      val f = if (l.startsWith("./")) {
        new File(cfg.sourceDir, l.substring(2))
      } else {
        new File(l)
      }

      if (f.exists()) {
        log.info(s"Directory for [${cfg.id}] is locked with file [${f.getAbsolutePath()}]")
        true
      } else {
        false
      }
  }

  protected def files() : List[File] = {
    val srcDir = new File(cfg.sourceDir)

    if (!srcDir.exists()) {
      srcDir.mkdirs()
    }

    if (!srcDir.exists() || !srcDir.isDirectory() || !srcDir.canRead()) {
      log.warning(s"Directory [$srcDir] for [${cfg.id}] does not exist or is not readable.")
      List.empty
    } else if (locked()) {
      List.empty
    } else {
      if (pending.isEmpty) {

        log.info(s"Executing directory scan for [${cfg.id}] for directory [${cfg.sourceDir}] with pattern [${cfg.pattern}]")

        try {
          val filter : DirectoryStream.Filter[Path] = new DirectoryStream.Filter[Path] {
            override def accept(entry: Path): Boolean = {
              entry.getParent().toFile().equals(srcDir) && cfg.pattern.forall(p => entry.toFile().getName().matches(p))
            }
          }

          val dirStream : DirectoryStream[Path] = Files.newDirectoryStream(srcDir.toPath(), filter)

          try {
            pending = dirStream.iterator().asScala.take(100).map(_.toFile()).toList
          } catch {
            case NonFatal(e) => log.warning(s"Error reading directory [${srcDir.getAbsolutePath()}] : [${e.getMessage()}]")
          } finally {
            dirStream.close()
          }
        }

        totalToProcess = pending.size
        log.info(s"Found [$totalToProcess] files to process from [$srcDir] with pattern [${cfg.pattern}]")
      }

      val result = pending.take(cfg.batchSize)
      pending = pending.drop(result.size)

      result
    }
  }

  protected def fileProcessor() : ActorRef = context.actorOf(Props[FileProcessActor])

  override def receive: Receive = idle

  private def idle : Receive = {
    case Tick =>
      if (sem.isDefined) {
        sem.foreach { s =>
          log.debug(s"Using semaphore actor [$s] to schedule file poll in [${cfg.id}]")
          s ! Acquire(self)
        }
      } else {
        self ! Acquired
      }

    case Waiting => // Do nothing - just wait

    case Acquired =>
      // First we get the batch files up next for processing
      val toProcess : List[File] = files()

      if (toProcess.nonEmpty) {
        // Schedule the processing of all files in the batch and pick up the results in the processing receive
        context.become(processing(toProcess, toProcess.size, 0, 0))
        toProcess.map { f =>
          fileProcessor().ask(FileProcessCmd(originalFile = f, cfg = cfg, handler = handler))(cfg.handleTimeout, self).pipeTo(self)
        }
      } else {
        sem.foreach(_ ! Release(self))
        // if the batch is empty we simply schedule the next directory scan
        context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
      }

    case FileCmdResult => // do nothing - its just the response to restore a file
  }

  private def checkProcessingComplete(remaining : List[File], total : Int, succeeded : Int, failed : Int) : Unit = {
    if (succeeded + failed == total) {
      log.info(s"Processed [${succeeded}] of [$totalToProcess](remaining [${pending.size}]) files in [${cfg.id}] from [${cfg.sourceDir}], ")
      // the batch is now complete, so we switch states and schedule the next tick
      context.become(idle)

      remaining.foreach { f =>
        if (!f.exists()) {
          log.debug(s"Restoring file [${f.getAbsolutePath()}]")
          val tempFile : File = new File(f.getParentFile, f.getName + cfg.tmpExt)
          context.actorOf(FileManipulationActor.props(cfg.operationTimeout)).tell(RenameFile(tempFile, f), self)
        }
      }

      sem.foreach(_ ! Release(self))

      if (pending.nonEmpty || failed == 0) {
        self ! Tick
      } else {
        context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
      }
    } else {
      // We are still waiting for file processors to respond
      context.become(processing(remaining, total, succeeded, failed))
    }
  }

  private def processing(remaining : List[File], total : Int, succeeded : Int, failed : Int) : Receive = {

    case akka.actor.Status.Failure(t) =>
      log.warning(s"Error executing file processor for dir [${cfg.sourceDir}] : [${t.getMessage()}]")
      checkProcessingComplete(remaining, total, succeeded, failed + 1)

    case r @ FileProcessResult(_, None) =>
      checkProcessingComplete(remaining.filter(_.getAbsolutePath() != r.cmd.originalFile.getAbsolutePath()), total, succeeded + 1, failed)

    case FileProcessResult(_, Some(_)) =>
      checkProcessingComplete(remaining, total, succeeded, failed + 1)
  }
}
