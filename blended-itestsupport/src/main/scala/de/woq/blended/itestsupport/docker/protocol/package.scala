package de.woq.blended.itestsupport.docker

package object protocol {

  case object StartContainerManager
  case object StopContainerManager
  case object ContainerManagerStarted
  case object ContainerManagerStopped

  case class StartContainer(name: String)
  case class ContainerStarted(name: String)
  case class DependenciesStarted(container: DockerContainer)

  case class StopContainer(name: String)
  case class ContainerStopped(name: String)

  case class GetContainerPorts(name: String)
  case class ContainerPorts(ports: Map[String, NamedContainerPort])

  case class InspectContainer(name: String)
}
