package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.DockerClient
import com.github.dockerjava.client.model.Ports
import org.slf4j.LoggerFactory

case class NamedContainerPort(name: String, sourcePort: Int)

private[docker] class DockerContainer(containerId: String, name: String)(implicit client: DockerClient) {

  var linkedContainers : List[String] = List.empty
  var ports : Map[String, NamedContainerPort] = Map.empty
  var exposedPorts : Option[Ports] = None

  private[DockerContainer] val logger = LoggerFactory.getLogger(classOf[DockerContainer].getName)
  private[DockerContainer] val container  = client.createContainerCmd(id).withName(name).exec()

  def id = containerId
  def containerName = name
  def links = linkedContainers

  def startContainer(exposedPorts: Ports) = {
    logger info s"Starting container [${name}] with port bindings [${exposedPorts}]."
    this.exposedPorts = Some(exposedPorts)
    client.startContainerCmd(containerName).withPortBindings(exposedPorts)
    this
  }

  def waitContainer = {
    logger info s"Waiting for container [${name}]"
    client.waitContainerCmd(containerName).exec()
    this
  }

  def stopContainer = {
    logger info s"Stopping container [${name}]"
    this.exposedPorts = None
    client.stopContainerCmd(containerName).exec()
    this
  }

  def withNamedPort(port: NamedContainerPort) = {
    this.ports += (port.name -> port)
  }

  def withNamedPorts(ports : Seq[NamedContainerPort]) = {
    ports.foreach(withNamedPort _)
    this
  }

  def withLink(link : String) = {
    this.linkedContainers = name :: this.linkedContainers
    this
  }

}
