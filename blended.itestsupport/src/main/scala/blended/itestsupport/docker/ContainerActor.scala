package blended.itestsupport.docker

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.util.Timeout
import blended.itestsupport.ContainerUnderTest
import blended.itestsupport.compress.TarFileSupport
import blended.itestsupport.docker.protocol._
import com.github.dockerjava.api.DockerClient
import akka.pattern.ask

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object ContainerActor {
  def apply(container: ContainerUnderTest)(implicit client: DockerClient) = new ContainerActor(container)
}

class ContainerActor(container: ContainerUnderTest)(implicit client: DockerClient) extends Actor with ActorLogging {

  private[this] val dc = new DockerContainer(container)
  private[this] implicit val eCtxt   = context.dispatcher
  implicit val timeout = new Timeout(5.seconds)

  case object PerformStart

  class ContainerStartActor() extends Actor with ActorLogging {

    def receive = LoggingReceive {
      case PerformStart =>
        dc.startContainer
        sender() ! ContainerStarted(Right(container))
        self ! PoisonPill
    }
  }

  def stopped : Receive = LoggingReceive {
    case StartContainer(n) if container.ctName == n  => {
      val starter = context.actorOf(Props( new ContainerStartActor()))
      starter ! PerformStart
      context.become(starting(sender()))
    }
  }

  def starting(requestor : ActorRef) : Receive = LoggingReceive {
    case msg : ContainerStarted =>
      requestor ! msg
      context become started(container)
  }

  def started(cut: ContainerUnderTest) : Receive = LoggingReceive {
    case wcd : WriteContainerDirectory =>
      val requestor = sender()

      try {
        requestor ! WriteContainerDirectoryResult(Right((wcd.container, dc.writeContainerDirectory(wcd.dir, wcd.content))))
      } catch {
        case NonFatal(e) => requestor ! WriteContainerDirectoryResult(Left(e))
      }

    case gcd : GetContainerDirectory =>
      val requestor = sender()

      try {
        val result = TarFileSupport.untar(dc.getContainerDirectory(gcd.dir))
        log.info(s"Extracted [${result.size}] entries for directory [${gcd.dir}] from container [${container.ctName}]")
        log.info(s"Extracted entries are [${result.keys.mkString(", ")}]")
        log.debug(s"Sending container director response to [${requestor.path}]")
        requestor ! GetContainerDirectoryResult(Right(ContainerDirectory(gcd.container, gcd.dir, result)))
      } catch {
        case NonFatal(t) => requestor ! GetContainerDirectoryResult(Left(t))
       }

    case exec : ExecuteContainerCommand =>
      val requestor = sender()

      dc.executeCommand(exec.user, exec.cmd:_*) match {
        case left @ Left(t) => requestor ! left
        case Right((execId, out, err))  =>
          (context.actorOf(WatchExecActor.props(dc, execId, out, err)) ? WatchExec)(exec.timeout).mapTo[ExecResult].onComplete {
            case Failure(t) => requestor ! ExecuteContainerCommandResult(Left(t))
            case Success(result) => requestor ! ExecuteContainerCommandResult(Right((container, result)))
          }
      }

    case StopContainer => {
      new DockerContainer(cut).stopContainer
      context become stopped
      log.debug(s"Sending stopped message to [$sender]")
      sender ! ContainerStopped(Right(container.ctName))
    }
  }

  def receive = stopped
}