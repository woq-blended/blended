package de.woq.blended.itestsupport.docker

import akka.actor.{Props, ActorLogging, Actor, ActorRef}
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import com.github.dockerjava.client.model.Ports.Binding
import com.github.dockerjava.client.model.{ExposedPort, Ports}

import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.Future

object ContainerActor {
  def apply(container: DockerContainer, portScanner: ActorRef) = new ContainerActor(container, portScanner)
}

class ContainerActor(container: DockerContainer, portScanner: ActorRef) extends Actor with ActorLogging {

  case class PerformStart(container: DockerContainer, ports: Ports)

  object ContainerStartActor {
    def apply() = new ContainerStartActor
  }

  class ContainerStartActor extends Actor with ActorLogging {

    def receive = LoggingReceive {
      case PerformStart(container, ports) =>
        container.startContainer(ports).waitContainer
        sender ! ContainerStarted(container.containerName)
    }
  }

  implicit val timeout = new Timeout(5.seconds)
  implicit val eCtxt   = context.dispatcher

  def stopped : Receive = LoggingReceive {
    case StartContainer(n) if container.containerName == n  => {
      portBindings
    }
    case p : Ports => {
      val starter = context.actorOf(Props(ContainerStartActor()))
      context become starting(sender)
      starter ! PerformStart(container, p)
    }
  }

  def starting(requestor : ActorRef) : Receive = LoggingReceive {
    case msg : ContainerStarted =>
      requestor ! msg
      context become started
  } orElse(getPorts)

  def started : Receive = LoggingReceive {
    case StopContainer(n) if container.containerName == n  => {
      val requestor = sender
      container.stopContainer
      context become stopped
      requestor ! ContainerStopped(container.containerName)
    }
  } orElse(getPorts)

  def getPorts : Receive = LoggingReceive {
    case GetContainerPorts(n) if container.containerName == n => {
      val ports : Map[String, NamedContainerPort] =
        container.ports.mapValues { namedPort =>
          val exposedPort = new ExposedPort("tcp", namedPort.sourcePort)
          val mapped = container.exposedPorts.get.getBindings.get(exposedPort)
          val realPort = mapped.getHostPort
          NamedContainerPort(namedPort.name, realPort)
        }
      log.debug(s"Sending [${ContainerPorts(ports)}] to [${sender}]")
      sender ! ContainerPorts(ports)
    }
  }

  def receive = stopped

  private def portBindings {
    val bindings = new Ports()

    // We create a Future for each port. The Future uses the underlying PortScanner
    // to retreive the next port number
    val portRequests : Iterable[Future[(NamedContainerPort, FreePort)]] =
      container.ports.values.map { case namedPort =>
        (portScanner ? GetPort).mapTo[FreePort].collect { case fp =>
          (namedPort, fp)
        }
    }

    // We create a single Future from the list of futures created before, collect the result
    // and then pass on the Bindings to ourselves.
    Future.sequence(portRequests).mapTo[Iterable[(NamedContainerPort, FreePort)]].collect { case ports =>
      ports.foreach { case (namedPort, freeport) =>
        bindings.bind(new ExposedPort("tcp", namedPort.sourcePort), new Binding(freeport.p))
      }
    } onSuccess { case _ => self ! bindings }
  }
}