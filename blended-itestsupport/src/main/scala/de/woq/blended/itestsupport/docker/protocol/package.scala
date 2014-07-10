package de.woq.blended.itestsupport.docker

package object protocol {

  case object StartContainerManager
  case object ContainerManagerStarted

  case class StartContainer(name: String)
  case class ContainerStarted(name: String)

  case class StopContainer(name: String)
  case class ContainerStopped(name: String)

  case class GetContainerPorts(name: String)
  case class ContainerPorts(ports: Map[String, NamedContainerPort])
}
