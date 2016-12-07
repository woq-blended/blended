package blended.itestsupport.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model._
import blended.itestsupport.ContainerUnderTest
import org.slf4j.LoggerFactory

/*
 * Provide a simple wrapper around the excellent docker Java API to create an abstraction suitable for
 * integration tests.
 */

class DockerContainer(cut: ContainerUnderTest)(implicit client: DockerClient) {

  private[this] val logger = LoggerFactory.getLogger(classOf[DockerContainer].getName)

  /**
   * @return The docker image id of the container.
   */
  private[this] def id = cut.imgId

  /**
   * @return The docker runtime name of the container.
   */
  private[this] def containerName = cut.dockerName

  /**
   * Start the container with a given set of exposed ports. Exposed ports are defined in terms of docker
   * port mappings and map zero or more exposed container ports to physical ports within the hosting
   * OS. The mapping can be injected while starting the container as the port mapping is usually calculated
   * by some manager object that knows about available ports or can determine available ports upon request.
   */
  def startContainer = {
    
    val links : List[Link] = cut.links.map { l => Link.parse(s"${l.container}:${l.hostname}") }
    logger info s"Starting container [${cut.dockerName}] with container links [$links]."
    
    val containerCmd  = client.createContainerCmd(id)
      .withName(cut.dockerName)
      .withTty(true)
      .withPublishAllPorts(true)

    if (!links.isEmpty) containerCmd.withLinks(links:_*)
    containerCmd.exec()

    client.startContainerCmd(containerName).exec()
    this
  }

  def containerInfo = client.inspectContainerCmd(containerName).exec()

  def removeContainer = {
    logger info s"Removing container [$containerName] from Docker."
    client.removeContainerCmd(containerName).withForce(true).withRemoveVolumes(true).exec()
  }
  
  def stopContainer = {
    logger info s"Stopping container [$containerName]"
    client.stopContainerCmd(containerName).exec()
    this
  }
}
