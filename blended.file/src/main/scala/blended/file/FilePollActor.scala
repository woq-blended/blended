package blended.file

import java.io.{File, FilenameFilter}

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import blended.akka.SemaphoreActor.{Acquire, Acquired, Release, Waiting}
import blended.util.logging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object FilePollActor {

  def props(
    cfg: FilePollConfig,
    handler: FilePollHandler,
    sem : Option[ActorRef] = None
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
  private[this ]implicit val timeout : Timeout = Timeout(FileManipulationActor.operationTimeout)

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
  }

  private[this] def locked() : Boolean = cfg.lock match {
    case None => false
    case Some(l) =>
      val f = if (l.startsWith("./"))
        new File(cfg.sourceDir, l.substring(2))
      else
        new File(l)

      if (f.exists()) {
        log.info(s"Directory for [${cfg.id}] is locked with file [${f.getAbsolutePath()}]")
        true
      } else {
        false
      }
  }

  private[this] def files() : List[File] = {
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

      srcDir.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = {
          if (cfg.pattern.isEmpty || cfg.pattern.forall(p => name.matches(p))) {
            val f = new File(dir, name)
            f.exists() && f.isFile() && f.canRead()
          } else {
            false
          }
        }
      }).toList
    }
  }

  override def receive: Receive = idle

  private[file] def idle : Receive = {
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

      val futures : Iterable[Future[FileProcessed]] = files().map { f =>
        context.actorOf(Props[FileProcessActor]).ask(FileProcessCmd(f, cfg, handler)).mapTo[FileProcessed]
      }

      val listFuture : Future[Iterable[FileProcessed]] = Future.sequence(futures)

      listFuture.onComplete { c =>
        c match {
          case Failure(t) =>
            log.warn(s"Error processing directory [${cfg.sourceDir}] in [${cfg.id}] : [${t.getMessage()}]")

          case Success(results) =>
            val succeeded = results.count(_.success)
            log.info(s"Processed [$succeeded] of [${results.size}] files in [${cfg.id}] from [${cfg.sourceDir}], ")
        }

        sem.foreach(_ ! Release(self))
        context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
      }
  }
}
