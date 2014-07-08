package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.DockerClient
import com.github.dockerjava.client.model.Ports.Binding
import com.github.dockerjava.client.model.{ExposedPort, Ports}
import de.woq.blended.itestsupport.PortScanner

case class NamedContainerPort(name: String, sourcePort: Int)

class DockerContainer(s: String)(implicit client: DockerClient) {

  private[DockerContainer] val container = client.createContainerCmd(s).exec()
  private[DockerContainer] var ports      : Option[Seq[NamedContainerPort]] = None
  private[DockerContainer] var namedPorts : Map[String, Int] = Map.empty

  def id = container.getId

  def startContainer   {
    client.startContainerCmd(id).withPortBindings(portBindings).exec()
  }

  def waitContainer    { client.waitContainerCmd(id).exec() }
  def stopContainer    { client.stopContainerCmd(id).exec() }
  def inspectContainer { client.inspectContainerCmd(id).exec() }

  def withNamedPorts(ports : NamedContainerPort*) = {
    this.ports = Some(ports)
    this
  }

  def port(name: String) = namedPorts.get(name)

  lazy val portBindings = {
    val bindings = new Ports()
    var nextPort = 1024;

    ports.foreach{_ foreach { namedPort =>
      val port = PortScanner.findFreePort(nextPort)
      bindings.bind(new ExposedPort("tcp", namedPort.sourcePort), new Binding(port))
      namedPorts += (namedPort.name -> port)

      nextPort = port + 1
    }}

    bindings
  }

}
