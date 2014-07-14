package de.woq.blended.itestsupport.docker

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive

import de.woq.blended.itestsupport.docker.protocol._

object DependentContainerActor {
  def apply(container : DockerContainer) = new DependentContainerActor(container)
}

class DependentContainerActor(container: DockerContainer) extends Actor with ActorLogging {

  var pendingContainers : List[String] = container.links

  def receive = LoggingReceive {
    case ContainerStarted(n) => {
      pendingContainers = pendingContainers.filter(_ != n)
      if (pendingContainers.isEmpty) {
        log info s"Dependencies for container [${container.id}] started."
        sender ! DependenciesStarted(container)
      }
    }
  }

}
