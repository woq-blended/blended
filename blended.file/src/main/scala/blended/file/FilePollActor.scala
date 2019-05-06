package blended.file

import java.io.{File, FilenameFilter}

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.{ask, pipe}
import blended.akka.SemaphoreActor.{Acquire, Acquired, Release, Waiting}
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object FilePollActor {

  val batchSize : Int = 10

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
) extends Actor {

  private val log : Logger = Logger[FilePollActor]
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
      log.info(s"Directory [$srcDir] for [${cfg.id}] does not exist or is not readable.")
      List.empty
    } else if (locked()) {
      List.empty
    } else {
      if (pending.isEmpty) {
        pending = srcDir.listFiles(new FilenameFilter {
          override def accept(dir: File, name: String): Boolean = {
            if (cfg.pattern.isEmpty || cfg.pattern.forall(p => name.matches(p))) {
              val f = new File(dir, name)
              f.exists() && f.isFile() && f.canRead()
            } else {
              false
            }
          }
        }).toList

        totalToProcess = pending.size
        log.info(s"Found [$totalToProcess] files to process from [$srcDir]")
      }

      val result = pending.take(FilePollActor.batchSize)
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
          log.trace(s"Using semaphore actor [$s] to schedule file poll in [${cfg.id}]")
          s ! Acquire(self)
        }
      } else {
        self ! Acquired
      }

    case Waiting => // Do nothing - just wait

    case Acquired =>
      log.info(s"Executing File Poll in [${cfg.id}] for directory [${cfg.sourceDir}]")

      // First we get the batch files up next for processing
      val toProcess : List[File] = files()

      if (toProcess.nonEmpty) {
        // Schedule the processing of all files in the batch and pick up the results in the processing receive
        context.become(processing(toProcess, List.empty, 0))
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

  private def checkProcessingComplete(batch : List[File], succeeded : List[FileProcessResult], failed : Int) : Unit = {
    if (succeeded.size + failed == batch.size) {
      log.info(s"Processed [${succeeded.size}] of [$totalToProcess](remaining [${pending.size}]) files in [${cfg.id}] from [${cfg.sourceDir}], ")
      // the batch is now complete, so we switch states and schedule the next tick
      context.become(idle)

      batch.foreach { f =>
        if (!f.exists()) {
          val tempFile : File = new File(f.getParentFile, f.getName + cfg.tmpExt)
          context.actorOf(FileManipulationActor.props(cfg.operationTimeout)).tell(RenameFile(tempFile, f), self)
        }
      }

      sem.foreach(_ ! Release(self))

      if (pending.nonEmpty) {
        self ! Tick
      } else {
        context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
      }
    } else {
      // We are still waiting for file processors to respond
      context.become(processing(batch, succeeded, failed))
    }
  }

  private def processing(batch : List[File], succeeded : List[FileProcessResult], failed : Int) : Receive = {
    case akka.actor.Status.Failure(t) =>
      log.warn(s"Error executing file processor for dir [${cfg.sourceDir}] : [${t.getMessage()}]")
      checkProcessingComplete(batch, succeeded, failed + 1)

    case r @ FileProcessResult(_, None) =>
      checkProcessingComplete(batch, r :: succeeded, failed)

    case FileProcessResult(_, Some(_)) =>
      checkProcessingComplete(batch, succeeded, failed + 1)
  }
}
