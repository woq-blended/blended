package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.DockerClient
import com.github.dockerjava.client.model.Ports.Binding
import com.github.dockerjava.client.model.{ExposedPort, Ports}
import de.woq.blended.itestsupport.PortScanner
import org.slf4j.LoggerFactory

case class NamedContainerPort(name: String, sourcePort: Int)

class DockerContainer(s: String)(implicit client: DockerClient) {

  private[DockerContainer] val logger = LoggerFactory.getLogger(classOf[DockerContainer].getName)

  private[DockerContainer] val container  = client.createContainerCmd(s).exec()
  private[DockerContainer] var ports      : Map[String, NamedContainerPort] = Map.empty
  private[DockerContainer] var namedPorts : Map[String, Int] = Map.empty
  private[DockerContainer] var name = s;

  def id = container.getId

  def startContainer = {
    logger info s"Starting container [${name}]"
    client.startContainerCmd(id).withPortBindings(portBindings).exec()
    this
  }

  def waitContainer = {
    logger info s"Waiting for container [${name}]"
    client.waitContainerCmd(id).exec()
    this
  }

  def stopContainer = {
    logger info s"Stopping container [${name}]"
    client.stopContainerCmd(id).exec()
    this
  }

  def inspectContainer = {
    client.inspectContainerCmd(id).exec()
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

  def port(name: String) = namedPorts.get(name)

  lazy val portBindings = {
    val bindings = new Ports()

    ports.values.foreach{ namedPort : NamedContainerPort =>
      val port = PortScanner.findFreePort
      bindings.bind(new ExposedPort("tcp", namedPort.sourcePort), new Binding(port))
      namedPorts += (namedPort.name -> port)
    }

    bindings
  }

}
