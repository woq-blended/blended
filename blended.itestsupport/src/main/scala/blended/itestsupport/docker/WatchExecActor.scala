package blended.itestsupport.docker

import java.io.ByteArrayOutputStream

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import blended.itestsupport.docker.protocol.ExecResult

import scala.concurrent.duration._

case object WatchExec

object WatchExecActor {

  def props(container: DockerContainer, execId: String, out: ByteArrayOutputStream, err: ByteArrayOutputStream) =
    Props(new WatchExecActor(container, execId, out, err))
}

class WatchExecActor(
  container: DockerContainer, execId: String, out: ByteArrayOutputStream, err: ByteArrayOutputStream
) extends Actor with ActorLogging {

  private[this] implicit val eCtxt = context.system.dispatcher

  case object Tick

  override def receive: Receive = {
    case WatchExec =>
      log.info(s"Monitoring exec instance [$execId]")
      context.become(watching(sender()))
      self ! Tick
  }

  def watching(requestor: ActorRef): Receive = {
    case Tick =>
      val inspect = container.inspectExec(execId)
      if (inspect.isRunning()) {
        log.debug(s"Exec instance [$execId] is still running ...")
        context.system.scheduler.scheduleOnce(100.millis, self, Tick)
      } else {
        log.info(s"Exec instance [$execId] has terminated. RC is [${inspect.getExitCode()}]")
        val result = ExecResult(execId, out.toByteArray(), err.toByteArray(), inspect.getExitCode())

        requestor ! result

        context.stop(self)
      }
  }
}
