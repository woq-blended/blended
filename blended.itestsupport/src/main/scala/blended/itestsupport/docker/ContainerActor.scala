package blended.itestsupport.docker

import java.io.{BufferedInputStream, ByteArrayOutputStream}

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.util.Timeout
import blended.itestsupport.ContainerUnderTest
import blended.itestsupport.compress.TarFileSupport
import blended.itestsupport.docker.protocol._
import blended.util.StreamCopySupport
import com.github.dockerjava.api.DockerClient
import org.kamranzafar.jtar.TarInputStream

import scala.collection.mutable
import scala.concurrent.duration._

object ContainerActor {
  def apply(container: ContainerUnderTest)(implicit client: DockerClient) = new ContainerActor(container)
}

class ContainerActor(container: ContainerUnderTest)(implicit client: DockerClient) extends Actor with ActorLogging {

  private[this] val dc = new DockerContainer(container)

  case object PerformStart

  class ContainerStartActor() extends Actor with ActorLogging {

    implicit val timeout = new Timeout(5.seconds)
    implicit val eCtxt   = context.dispatcher

    def receive = LoggingReceive {
      case PerformStart =>
        dc.startContainer
        sender ! ContainerStarted(Right(container))
        self ! PoisonPill
    }
  }

  def stopped : Receive = LoggingReceive {
    case StartContainer(n) if container.ctName == n  => {
      val starter = context.actorOf(Props( new ContainerStartActor()))
      starter ! PerformStart
      context.become(starting(sender))
    }
  }

  def starting(requestor : ActorRef) : Receive = LoggingReceive {
    case msg : ContainerStarted =>
      requestor ! msg
      context become started(container)
  }

  def started(cut: ContainerUnderTest) : Receive = LoggingReceive {
    case gcd : GetContainerDirectory =>
      val result = TarFileSupport.untar(dc.getContainerDirectory(gcd.dir))
      log.info(s"Extracted [${result.size}] entries for directory [${gcd.dir}] from container [${container.ctName}]")
      sender() ! ContainerDirectory(result)

    case StopContainer => {
      new DockerContainer(cut).stopContainer
      context become stopped
      log.debug(s"Sending stopped message to [$sender]")
      sender ! ContainerStopped(Right(container.ctName))
    }
  }

  def receive = stopped
}