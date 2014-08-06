package de.woq.blended.itestsupport.docker

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive

import de.woq.blended.itestsupport.docker.protocol._

/**
 * This is a helper Actor that works on behalf of the #ContainerManager to delay
 * the start of a container until all linked containers have been started.
 *
 * One instance of this actor is created for every defined container that has
 * at least one container link defined. The ContainerManager propagates the
 * ContainerStarted events. These events will be used to clear the list of
 * containers that we are waiting for. Once the list is Empty we will send
 * an DependenciesStarted message to the ContainerManager, so the he can start
 * the container afterwards.
 */
class DependentContainerActor(container: DockerContainer) extends Actor with ActorLogging {

  // Initialize the
  var pendingContainers : List[String] = container.links.map(_.getName)

  def receive = LoggingReceive {
    case ContainerStarted(n) => {
      pendingContainers = pendingContainers.filter(_ != n)
      if (pendingContainers.isEmpty) {
        log info s"Dependencies for container [${container.id}] started."
        sender ! DependenciesStarted(container)
        context.stop(self)
      }
    }
  }

}

object DependentContainerActor {
  def apply(container : DockerContainer) = new DependentContainerActor(container)
}
