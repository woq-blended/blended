package de.woq.blended.itestsupport.docker

import de.woq.blended.itestsupport.PortScanner

import scala.concurrent.duration._
import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.event.{LoggingAdapter, LoggingReceive}
import akka.util.Timeout
import com.typesafe.config.Config
import de.woq.blended.itestsupport.docker.protocol._

class ContainerManager extends Actor with ActorLogging with Docker {

  implicit val timeout = Timeout(5.seconds)
  implicit val eCtxt   = context.dispatcher

  override val config: Config = context.system.settings.config
  override val logger: LoggingAdapter = context.system.log

  def initializing : Receive = LoggingReceive {
    case StartContainerManager => {
      log info s"Initializing Container manager"
      val portScanner = context.actorOf(Props(PortScanner()), "PortScanner")
      configuredContainers.foreach{ case(name, ct) =>
        val actor = context.actorOf(Props(ContainerActor(ct, portScanner)), name)
        actor ! StartContainer(name)
      }
      context become running
      log info "Container Manager started."
      sender ! ContainerManagerStarted
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

  def receive = initializing

  private def containerActor(name: String) = context.actorSelection(name).resolveOne()
}
