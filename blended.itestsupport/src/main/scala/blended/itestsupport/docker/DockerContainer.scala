package blended.itestsupport.docker

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream }

import scala.util.control.NonFatal

import blended.itestsupport.ContainerUnderTest
import blended.util.logging.Logger
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.InspectExecResponse
import com.github.dockerjava.api.model._
import com.github.dockerjava.core.command.ExecStartResultCallback

/*
 * Provide a simple wrapper around the excellent docker Java API to create an abstraction suitable for
 * integration tests.
 */

class DockerContainer(cut: ContainerUnderTest)(implicit client: DockerClient) {

  private[this] val logger = Logger[DockerContainer]

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

    val containerCmd  = client.createContainerCmd(cut.imgId)
      .withName(cut.dockerName)
      .withTty(true)
      .withPortBindings(ports:_*)
      .withEnv(env:_*)

    if (!links.isEmpty) containerCmd.withLinks(links:_*)
    containerCmd.exec()

    client.startContainerCmd(cut.dockerName).exec()
    this
  }

  def executeCommand(user: String, cmd: String*) : Either[Throwable, (String, ByteArrayOutputStream, ByteArrayOutputStream)] = {

    logger.info(s"Executing cmd [${cmd.foldLeft(""){case (v,e) => v + " " + e }}] for user [$user]")

    try {
      val command = client.execCreateCmd(cut.dockerName)
        .withUser(user)
        .withCmd(cmd:_*)
        .withAttachStdout(true)
        .withAttachStderr(true)

      val execId : String = command.exec().getId()

      val out = new ByteArrayOutputStream()
      val err = new ByteArrayOutputStream()

      val rcb = new ExecStartResultCallback(out, err)

      val startExec = client.execStartCmd(execId).withTty(true)
      startExec.exec[ExecStartResultCallback](rcb)

      Right((execId, out, err))
    } catch {
      case NonFatal(e) => Left(e)
    }
  }

  def inspectExec(id: String) : InspectExecResponse = client.inspectExecCmd(id).withExecId(id).exec()

  def getContainerDirectory(dir: String) : InputStream = {
    logger.info(s"Getting directory [$dir] from container [${cut.ctName}]")
    client.copyArchiveFromContainerCmd(cut.dockerName, dir).exec()
  }

  def writeContainerDirectory(dir: String, content: Array[Byte]) : Boolean = {
    try {
      logger.info(s"Writing archive of size [${content.length}] to directory [$dir] in container [${cut.ctName}]")
      val cmd = client.copyArchiveToContainerCmd(cut.dockerName)
      cmd.withRemotePath(dir).withTarInputStream(new ByteArrayInputStream(content))
      cmd.exec()
      true
    } catch {
      case NonFatal(t) => false
    }
  }

  def containerInfo = client.inspectContainerCmd(cut.dockerName).exec()

  def removeContainer = {
    logger info s"Removing container [${cut.dockerName}] from Docker."
    client.removeContainerCmd(cut.dockerName).withForce(true).withRemoveVolumes(true).exec()
  }
  
  def stopContainer = {
    logger info s"Stopping container [${cut.dockerName}]"
    client.stopContainerCmd(cut.dockerName).exec()
    this
  }
}
