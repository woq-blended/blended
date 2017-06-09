package blended.file

import java.io.{File, FilenameFilter}

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future

object FilePollActor {

  def props(cfg: FilePollConfig, handler: FilePollHandler) : Props =
    Props(new FilePollActor(cfg, handler))
}

class FilePollActor(cfg: FilePollConfig, handler: FilePollHandler) extends Actor with ActorLogging {

  case object Tick

  implicit val eCtxt = context.system.dispatcher
  implicit val timeout = Timeout(FileManipulationActor.operationTimeout)

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
  }

  private[this] def locked() : Boolean = cfg.lock match {
    case None => false
    case Some(l) =>
      val f = new File(cfg.sourceDir, l)

      if (f.exists()) {
        log.info(s"Directory is locked with file [${f.getAbsolutePath()}]")
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
      log.info(s"Directory [$srcDir] does not exist or is not readable.")
      List.empty
    } else if (locked()) {
      List.empty
    } else {

      srcDir.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = {
          if (cfg.pattern.isEmpty || cfg.pattern.forall(p => name.matches(p))) {
            val f = new File(dir, name)
            f.exists() && f.isFile() && f.canRead()
          } else false
        }
      }).toList
    }
  }

  override def receive: Receive = {
    case Tick =>
      log.info(s"Executing File Poll for directory [${cfg.sourceDir}]")

      val futures : Iterable[Future[FileProcessed]] = files().map { f =>
        context.actorOf(Props[FileProcessActor]).ask(FileProcessCmd(f, cfg, handler)).mapTo[FileProcessed]
      }

      val listFuture : Future[Iterable[FileProcessed]] = Future.sequence(futures)

      listFuture.onSuccess {
        case results =>
          val succeeded = results.count(_.success)
          log.info(s"Processed [$succeeded] of [${results.size}] files from [${cfg.sourceDir}], ")

          context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
      }
  }
}
