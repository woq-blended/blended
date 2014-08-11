package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.DockerClient
import com.github.dockerjava.client.model.{Bind, Volume, Link, Ports}
import org.slf4j.LoggerFactory

/*
 * Provide a simple wrapper around the excellent docker Java API to create an abstraction suitable for
 * integration tests.
 */

case class NamedContainerPort(name: String, sourcePort: Int)

private[docker] class DockerContainer(containerId: String, name: String)(implicit client: DockerClient) {

  var linkedContainers : List[Link] = List.empty
  var mappedVolumes : List[Bind] = List.empty
  var ports : Map[String, NamedContainerPort] = Map.empty
  var exposedPorts : Option[Ports] = None

  private[DockerContainer] val logger = LoggerFactory.getLogger(classOf[DockerContainer].getName)
  private[DockerContainer] val container  = client.createContainerCmd(id).withName(name).withTTY(true).exec()

  /**
   * @return The docker image id of the container.
   */
  def id = containerId

  /**
   * @return The docker runtime name of the container.
   */
  def containerName = name

  /**
   * @return A list of runtime names that the container relies on in terms of docker links.
   */
  def links = linkedContainers

  def binds = mappedVolumes

  /**
   * Start the container with a given set of exposed ports. Exposed ports are defined in terms of docker
   * port mappings and map zero or more exposed container ports to physical ports within the hosting
   * OS. The mapping can be injected while starting the container as the port mapping is usually calculated
   * by some manager object that knows about available ports or can determine available ports upon request.
   */
  def startContainer(exposedPorts: Ports) = {
    logger info s"Starting container [${name}] with port bindings [${exposedPorts}] and container links [${linkedContainers}]."
    this.exposedPorts = Some(exposedPorts)

    val cmd = client.startContainerCmd(containerName)
    if (!exposedPorts.getBindings.isEmpty) cmd.withPortBindings(exposedPorts)
    if (!linkedContainers.isEmpty) cmd.withLinks(links:_*)
    if (!mappedVolumes.isEmpty) cmd.withBinds(binds:_*)

    cmd.exec()

    this
  }

  /**
   * Simply expose the wait operation of the container. This is usually called after the container has been
   * started.
   */
  def waitContainer = {
    logger info s"Waiting for container [${name}]"
    client.waitContainerCmd(containerName).exec()
    this
  }

  /**
   * Simply expose the stop operation of the container.
   */
  def stopContainer = {
    logger info s"Stopping container [${name}]"
    this.exposedPorts = None
    client.stopContainerCmd(containerName).exec()
    this
  }

  def withNamedPort(port: NamedContainerPort) = {
    this.ports += (port.name -> port)
    this
  }

  def withNamedPorts(ports : Seq[NamedContainerPort]) = {
    ports.foreach(withNamedPort _)
    this
  }

  def withLink(link : String) = {
    this.linkedContainers = Link.parse(link) :: this.linkedContainers
    this
  }

  def withVolume(mappedDir : String, volume: Volume, rw: Boolean) = {
    this.mappedVolumes = new Bind(mappedDir, volume, rw) :: mappedVolumes
    this
  }

}
