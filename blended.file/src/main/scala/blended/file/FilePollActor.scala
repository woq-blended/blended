package blended.file

import akka.actor.{Actor, ActorLogging}

class FilePollActor(cfg: FilePollConfig, handler: FilePollHandler) extends Actor with ActorLogging {

  case object Tick

  implicit val eCtxt = context.system.dispatcher

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(cfg.interval, self, Tick)
  }

  override def receive: Receive = {
    case Tick => log.info("Executing File Poll")
  }
}
