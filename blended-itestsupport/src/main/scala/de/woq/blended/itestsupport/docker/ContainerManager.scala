package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.DockerClient
import de.woq.blended.itestsupport.PortScanner

import scala.concurrent.duration._
import akka.actor._
import akka.event.{LoggingAdapter, LoggingReceive}
import akka.util.Timeout
import com.typesafe.config.Config
import de.woq.blended.itestsupport.docker.protocol._

trait DockerClientProvider {
  def getClient : DockerClient
}

class ContainerManager extends Actor with ActorLogging with Docker { this:  DockerClientProvider =>

  implicit val timeout = Timeout(30.seconds)
  implicit val eCtxt   = context.dispatcher

  override val config: Config = context.system.settings.config
  override val logger: LoggingAdapter = context.system.log
  implicit val client = getClient

  var portScanner : ActorRef = _
  var pendingContainer : Map [String, ActorRef] = Map.empty
  var requestor : ActorRef = _

  def starting : Receive = LoggingReceive {
    case StartContainerManager => {
      log info s"Initializing Container manager"
      shutDownContainers()
      requestor = sender
      portScanner = context.actorOf(Props(PortScanner()), "PortScanner")
      configuredContainers.foreach{ case(name, ct) =>
        if (ct.links.isEmpty) {
          val actor = context.actorOf(Props(ContainerActor(ct, portScanner)), name)
          actor ! StartContainer(name)
        } else {
          val actor = context.actorOf(Props(DependentContainerActor(ct)))
          pendingContainer += (ct.containerName -> actor)
        }
      }
      if (checkPending) context become running
    }
    case DependenciesStarted(ct) => {
      sender ! PoisonPill
      pendingContainer -= ct.id
      val actor = context.actorOf(Props(ContainerActor(ct, portScanner)), ct.containerName)
      actor ! StartContainer(ct.containerName)
      if (checkPending) context become running
    }
    case ContainerStarted(name) => {
      pendingContainer.filterKeys( key => key != name ).values.foreach(a => a.forward(ContainerStarted(name)))
    }
  }

  def running : Receive = LoggingReceive {
    case GetContainerPorts(name) => {
      val requestor = sender
      containerActor(name).mapTo[ActorRef].onSuccess { case ct =>
        ct.tell(GetContainerPorts(name), requestor)
      }
    }
  }

  def receive = starting

  private def containerActor(name: String) = context.actorSelection(name).resolveOne()

  private def checkPending = {
    if (pendingContainer.isEmpty) {
      log info "Container Manager started."
      sender ! ContainerManagerStarted
      true
    } else false
  }
}
