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
  case class WriteContainerDirectoryResult(result : Either[Throwable, (ContainerUnderTest, Boolean)])

  case class ExecuteContainerCommand(container: ContainerUnderTest, timeout: FiniteDuration, user: String, cmd: String*)
  case class ExecResult(execId : String, out: Array[Byte], err: Array[Byte], rc : Int)
  case class ExecuteContainerCommandResult(result : Either[Throwable, (ContainerUnderTest, ExecResult)])

  case class GetContainerDirectory(container: ContainerUnderTest, dir: String)

  case class ContainerDirectory(container: ContainerUnderTest, dir: String, content: Map[String, Array[Byte]])
  case class GetContainerDirectoryResult(result: Either[Throwable, ContainerDirectory])

}