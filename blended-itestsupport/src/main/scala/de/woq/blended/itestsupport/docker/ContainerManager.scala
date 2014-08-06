package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.DockerClient
import de.woq.blended.itestsupport.PortScanner

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
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
  var runningContainer : Map [String, ActorRef] = Map.empty
  var requestor : Option[ActorRef] = _

  def starting : Receive = LoggingReceive {
    case StartContainerManager => {
      log info s"Initializing Container manager"
      shutDownContainers()
      requestor = Some(sender)
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
      runningContainer += (name -> sender)
      pendingContainer.values.foreach { _ ! ContainerStarted(name) }
    }
  }

  def running : Receive = LoggingReceive {
    case ContainerStarted(name) => {
      runningContainer += (name -> sender)
    }
    case GetContainerPorts(name) => {
      val requestor = sender
      containerActor(name).mapTo[ActorRef].onSuccess { case ct =>
        ct.tell(GetContainerPorts(name), requestor)
      }
    }
    case StopContainerManager => {
      val requestor = sender

      log debug s"Stopping container [${runningContainer}]"

      val stopFutures = runningContainer.collect {
        case (name, ctActor) => (ctActor ? StopContainer(name)).mapTo[ContainerStopped]
      }

      val stopped = Future.sequence(stopFutures).map( _ => requestor ! ContainerManagerStopped )

      context stop(self)
    }
  }

  def receive = starting

  private def containerActor(name: String) = context.actorSelection(name).resolveOne()

  private def checkPending = {
    if (pendingContainer.isEmpty) {
      log info "Container Manager started."
      requestor.foreach { _ ! ContainerManagerStarted }
      true
    } else false
  }
}
