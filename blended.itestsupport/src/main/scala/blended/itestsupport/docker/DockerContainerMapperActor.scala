package blended.itestsupport.docker

import scala.collection.JavaConverters._

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import blended.itestsupport.{ContainerUnderTest, NamedContainerPort}
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.{Container, ContainerPort}

class DockerContainerMapperActor extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case InternalMapDockerContainers(requestor, cuts, client) =>
      log.debug(s"Mapping docker containers $cuts")

      val mapped : Map[String, Either[Throwable, ContainerUnderTest]] = cuts.map { case (name, cut) =>
        dockerContainer(cut, client) match {
          case e if e.isEmpty => (name, Left(new Exception(s"No suitable docker container found for [${cut.ctName}]")))
          case head :: rest if rest.isEmpty => (name, Right(mapDockerContainer(head, cut)))
          case _ => (name, Left(new Exception(s"No unique docker container found for [${cut.ctName}]")))
        }
      }

      val errors = mapped.values.filter(_.isLeft).map(_.left.get.getMessage)
      val mappedCuts = mapped.values.filter(_.isRight).map(_.right.get)

      val result = errors match {
        case e if e.isEmpty => InternalDockerContainersMapped(requestor, Right(mappedCuts.map { c => (c.ctName, c) }.toMap ))
        case l => InternalDockerContainersMapped(requestor, Left(new Exception(errors.mkString(","))))
      }

      log.debug(s"$result")
      sender ! result
  }

  private[docker] def mapDockerContainer(dc: Container, cut: ContainerUnderTest) : ContainerUnderTest = {
    val mapped = cut.ports.map { case (name, port) =>
      (name, mapPort(dc.getPorts, port))
    }

    cut.copy(dockerName = rootName(dc), ports = mapped)
  }

  private[docker] def rootName(dc : Container) : String =
    dc.getNames.filter { _.indexOf("/", 1) == -1 }.head.substring(1)


  private[docker] def mapPort(dockerPorts: Array[ContainerPort], port: NamedContainerPort) : NamedContainerPort = {
    dockerPorts.filter { _.getPrivatePort() == port.privatePort }.toList match {
      case e if e.isEmpty => port
      case l => port.copy(publicPort = l.head.getPublicPort)
    }
  }

  private[docker] def dockerContainer(cut: ContainerUnderTest, client: DockerClient) : List[Container] = {

    val dc = client.listContainersCmd().exec().asScala.toList

    dockerContainerByName(cut, dc) match {
      case e if e.isEmpty => dockerContainerByImage(cut, dc)
      case l => l
    }
  }

  private[docker] def dockerContainerByName(cut: ContainerUnderTest, dc: List[Container]) : List[Container] = {
    log.debug(s"Matching Docker Container by name: [${cut.dockerName}]")
    dc.filter(_.getNames.contains(s"/${cut.dockerName}"))
  }

  private[docker] def dockerContainerByImage(cut: ContainerUnderTest, dc: List[Container]) : List[Container] = {
    log.debug(s"Matching Docker Container by Image: [${cut.imgPattern}]")
    dc.filter(_.getImage.matches(cut.imgPattern))
  }
}
