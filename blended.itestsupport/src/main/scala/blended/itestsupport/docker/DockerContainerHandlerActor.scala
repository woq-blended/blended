package blended.itestsupport.docker

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import akka.pattern._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.util.Timeout
import blended.itestsupport.ContainerUnderTest
import com.github.dockerjava.api.DockerClient

import scala.concurrent.duration._
import blended.itestsupport.docker.protocol._

class DockerContainerHandler(client: DockerClient) extends Actor with ActorLogging {

  implicit private[this] val timeout: Timeout = Timeout(3.seconds)
  implicit private[this] val eCtxt: ExecutionContextExecutor = context.system.dispatcher

  def receive = LoggingReceive {
    case InternalStartContainers(cuts) =>
      log.info(s"Starting docker containers [$cuts]")

      val noDeps   = cuts.values.filter( _.links.isEmpty ).toList
      val withDeps = cuts.values.filter( _.links.nonEmpty).toList

      val pending  = withDeps.map { cut =>
        ( context.actorOf(DependentContainerActor.props(cut)), cut )
      }

      noDeps.foreach{ startContainer }

      context.become(starting(sender(), pending, noDeps, List.empty))
    case scm : StopContainerManager =>
      sender() ! ContainerManagerStopped
      context.stop(self)
  }

  def starting(
                requestor           : ActorRef,
                pendingContainers   : List[(ActorRef, ContainerUnderTest)],
                startingContainers  : List[ContainerUnderTest],
                runningContainers   : List[ContainerUnderTest]
              ) : Receive = LoggingReceive {
    case ContainerStarted(result) => result match {
      case Right(cut) =>
        pendingContainers.foreach { _._1 ! ContainerStarted(Right(cut)) }
        val remaining = startingContainers.filter(_ != cut)
        val started = cut :: runningContainers

        if (pendingContainers.isEmpty && remaining.isEmpty) {
          log.info(s"Container Manager started [$started]")
          context.become(running(started))
          requestor ! InternalContainersStarted(Right(started.map { ct => (ct.ctName, ct) }.toMap ))
        } else {
          context.become(starting(requestor, pendingContainers, remaining, started))
        }
      case Left(e) =>
        log error s"Error in starting docker containers [${e.getMessage}]"
        requestor ! Left(e)
        context.stop(self)
    }
    case DependenciesStarted(result) => result match {
      case Right(cut) =>
        val pending  = pendingContainers.filter(_._1 != sender())
        startContainer(cut)
        context.become(starting(requestor, pending, cut :: startingContainers, runningContainers))
      case Left(e) =>
        log.error("DependenciesStarted received with an Left (means: error)", e)
        context.stop(self)
    }
  }

  def running(managedContainers: List[ContainerUnderTest]) : Receive = LoggingReceive {
    case gcd: GetContainerDirectory =>
      val requestor = sender()
      context.actorSelection(gcd.container.ctName).resolveOne().foreach(_.tell(gcd, requestor))

    case wcd : WriteContainerDirectory =>
      val requestor = sender()
      context.actorSelection(wcd.container.ctName).resolveOne().foreach(_.tell(wcd, requestor))

    case exec : ExecuteContainerCommand =>
      val requestor = sender()
      context.actorSelection(exec.container.ctName).resolveOne().foreach(_.tell(exec, requestor))

    case scm : StopContainerManager =>

      log.info(s"Stopping Docker Container handler [$managedContainers]")

      implicit val timeout = new Timeout(scm.timeout)
      implicit val eCtxt = context.system.dispatcher
      val requestor = sender()

      val stopFutures : Seq[Future[ContainerStopped]] = managedContainers.map { cut =>
        for{
          actor <- context.actorSelection(cut.ctName).resolveOne()
          stopped <- (actor ? StopContainer).mapTo[ContainerStopped]
        } yield stopped
      }

      val r = Await.result(Future.sequence(stopFutures), scm.timeout)
      log.debug(s"Stopped Containers [$r]")
      requestor ! ContainerManagerStopped

      context.stop(self)
  }

  private[this] def startContainer(cut : ContainerUnderTest) : ActorRef = {

    val actor = context.actorOf(ContainerActor.props(cut, client), cut.ctName)
    actor ! StartContainer(cut.ctName)

    log.debug(s"Container Actor is [$actor]")

    actor
  }

}

object DockerContainerHandler {
  def props(dockerClient: DockerClient): Props = Props(new DockerContainerHandler(dockerClient))
}
