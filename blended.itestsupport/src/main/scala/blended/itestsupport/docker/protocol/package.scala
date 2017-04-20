package blended.itestsupport.docker

import blended.itestsupport.ContainerUnderTest

import scala.concurrent.duration.FiniteDuration

package object protocol {
  type DockerResult[T] = Either[Exception, T]
}

package protocol {

  case class StartContainerManager(containerUnderTest: Map[String, ContainerUnderTest])
  case class StopContainerManager(timeout: FiniteDuration)

  case class ContainerManagerStarted(containerUnderTest: DockerResult[Map[String, ContainerUnderTest]])
  case object ContainerManagerStopped

  case class StartContainer(name: String)
  case class ContainerStarted(cut: DockerResult[ContainerUnderTest])
  case class DependenciesStarted(container: DockerResult[ContainerUnderTest])

  case object StopContainer
  case class ContainerStopped(name: DockerResult[String])

  case class WriteContainerDirectory(container: ContainerUnderTest, dir: String, content: Array[Byte])

  case class GetContainerDirectory(container: ContainerUnderTest, dir: String)
  case class ContainerDirectory(container: ContainerUnderTest, dir: String, content: Map[String, Array[Byte]])

}