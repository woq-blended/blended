package blended.itestsupport.docker

import java.io.{ByteArrayInputStream, InputStream}

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model._
import blended.itestsupport.ContainerUnderTest
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

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
    logger info s"Links for container [${cut.dockerName}] : [$links]."

    val ports : Array[PortBinding] = cut.ports.map { case (name, p) => p.binding }.toArray
    logger info s"Ports for container [${cut.dockerName}] : [${cut.ports}]."

    val env : Array[String] = cut.env.map{ case (k,v) => s"$k=$v" }.toArray

    val containerCmd  = client.createContainerCmd(id)
      .withName(cut.dockerName)
      .withTty(true)
      .withPortBindings(ports:_*)
      .withEnv(env:_*)

    if (!links.isEmpty) containerCmd.withLinks(links:_*)
    containerCmd.exec()

    client.startContainerCmd(containerName).exec()
    this
  }

  def getContainerDirectory(dir: String) : InputStream = {
    logger.info(s"Getting directory [$dir] from container [${cut.ctName}]")
    client.copyArchiveFromContainerCmd(containerName, dir).exec()
  }

  def writeContainerDirectory(dir: String, content: Array[Byte]) : Either[Throwable, Boolean]= {
    try {
      logger.info(s"Writing archive of size [${content.length}] to directory [$dir] in container [${cut.ctName}]")
      val cmd = client.copyArchiveToContainerCmd(containerName)
      cmd.withRemotePath(dir).withTarInputStream(new ByteArrayInputStream(content))
      cmd.exec()
      Right(true)
    } catch {
      case NonFatal(t) => Left(t)
    }
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
