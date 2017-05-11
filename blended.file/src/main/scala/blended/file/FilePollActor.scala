package blended.file

import java.io.{File, FilenameFilter}

import akka.actor.{Actor, ActorLogging, Props}

object FilePollActor {

  def props(cfg: FilePollConfig, handler: FilePollHandler) : Props =
    Props(new FilePollActor(cfg, handler))
}

class FilePollActor(cfg: FilePollConfig, handler: FilePollHandler) extends Actor with ActorLogging {

  case object Tick

  implicit val eCtxt = context.system.dispatcher

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

      files().foreach { f=>
        log.info(s"Processing file [${f.getAbsolutePath()}]")
      }

      context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
  }
}
