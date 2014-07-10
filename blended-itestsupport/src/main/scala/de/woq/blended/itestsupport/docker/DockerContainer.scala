package de.woq.blended.itestsupport.docker

import akka.actor.{ActorRef, Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._
import com.github.dockerjava.client.{model, DockerClient}
import com.github.dockerjava.client.model.Ports.Binding
import com.github.dockerjava.client.model.{ExposedPort, Ports}
import org.slf4j.LoggerFactory

import de.woq.blended.itestsupport.protocol._
import de.woq.blended.itestsupport.docker.protocol._

case class NamedContainerPort(name: String, sourcePort: Int)

object ContainerActor {
  def apply(container: DockerContainer, portScanner: ActorRef) = new ContainerActor(container, portScanner)
}

class ContainerActor(container: DockerContainer, portScanner: ActorRef) extends Actor with ActorLogging {

  implicit val timeout = new Timeout(5.seconds)
  implicit val eCtxt   = context.dispatcher

  def stopped : Receive = {
    case StartContainer(n) if container.name == n  => {
      portBindings
    }
    case p : Ports => {
      container.startContainer(p).waitContainer
      context become ( LoggingReceive { started orElse common } )
      sender ! ContainerStarted
    }
  }

  def started : Receive = {
    case StopContainer(n) if container.name == n  => {
      container.stopContainer
      context become LoggingReceive { stopped orElse common }
      sender ! ContainerStopped
    }
  }

  def common : Receive = {
    case GetContainerPorts(n) if container.name == n => {
      val ports : Map[String, NamedContainerPort] =
        container.ports.mapValues { namedPort =>
          val exposedPort = new ExposedPort("tcp", namedPort.sourcePort)
          val mapped = container.exposedPorts.get.getBindings.get(exposedPort)
          val realPort = mapped.getHostPort
          NamedContainerPort(namedPort.name, realPort)
        }
      sender ! ContainerPorts(ports)
    }
  }

  def receive = LoggingReceive { stopped orElse common }

  def portBindings {
    val bindings = new Ports()

    val portRequests : Iterable[Future[(NamedContainerPort, FreePort)]]=
      container.ports.values.map { case namedPort =>
        (portScanner ? GetPort).mapTo[FreePort].collect { case fp =>
          (namedPort, fp)
        }
    }

    Future.sequence(portRequests).mapTo[Iterable[(NamedContainerPort, FreePort)]].collect { case ports =>
      ports.foreach { case (namedPort, freeport) =>
        bindings.bind(new ExposedPort("tcp", namedPort.sourcePort), new Binding(freeport.p))
      }
    } onSuccess { case _ => self ! bindings }
  }
}

private[docker] class DockerContainer(s: String)(implicit client: DockerClient) {

  var ports : Map[String, NamedContainerPort] = Map.empty
  var name  = s
  var exposedPorts : Option[Ports] = None

  private[DockerContainer] val logger = LoggerFactory.getLogger(classOf[DockerContainer].getName)
  private[DockerContainer] val container  = client.createContainerCmd(s).exec()

  def id = container.getId

  def startContainer(exposedPorts: Ports) = {
    logger info s"Starting container [${name}] with port bindings [${exposedPorts}]."
    this.exposedPorts = Some(exposedPorts)
    client.startContainerCmd(id).withPortBindings(exposedPorts).exec()
    this
  }

  def waitContainer = {
    logger info s"Waiting for container [${name}]"
    client.waitContainerCmd(id).exec()
    this
  }

  def stopContainer = {
    logger info s"Stopping container [${name}]"
    this.exposedPorts = None
    client.stopContainerCmd(id).exec()
    this
  }

  def withNamedPort(port: NamedContainerPort) = {
    this.ports += (port.name -> port)
  }

  def withNamedPorts(ports : Seq[NamedContainerPort]) = {
    ports.foreach(withNamedPort _)
    this
  }

  def withName(name : String) = {
    this.name = name
    this
  }
}
