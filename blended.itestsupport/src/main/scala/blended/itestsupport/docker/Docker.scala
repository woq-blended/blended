package blended.itestsupport.docker

import akka.event.LoggingAdapter
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.{Container, Image}
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder}
import com.typesafe.config.Config

import scala.collection.JavaConverters._

object DockerClientFactory {

  var client : Option[DockerClient] = None
  
  def apply(config : Config)(implicit logger: LoggingAdapter) = client match {
    case Some(dockerClient) => dockerClient
    case _ =>

      val configDockerHost = Option(config.getString("docker.host")).getOrElse("")
      val dockerHost = if (configDockerHost.startsWith("unix://")) {
        configDockerHost
      } else if (configDockerHost.isEmpty) {
        "unix:///var/run/docker.sock"
      } else {
        "tcp://" + config.getString("docker.host") + ":" + config.getString("docker.port")
      }

      val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(dockerHost)
        .withRegistryUsername(config.getString("docker.user"))
        .withRegistryPassword(config.getString("docker.password"))
        .withRegistryEmail(config.getString("docker.eMail"))
        .build()

      logger.info(s"Trying to connect to docker at [${dockerConfig.getDockerHost()}]")

      val dockerClient = DockerClientBuilder.getInstance(dockerConfig).build()

      val version = dockerClient.versionCmd().exec()

      logger info
        s"""
       Using Docker version  ${version.getVersion}
       Docker API version    ${version.getApiVersion}
       Docker Go version     ${version.getGoVersion}
       Architecture          ${version.getArch}
       Kernel version        ${version.getKernelVersion}"""

      client = Some(dockerClient)
      dockerClient.asInstanceOf[DockerClient]
    }
}

trait Docker {

  implicit val logger  : LoggingAdapter
  implicit val config  : Config
  implicit val client  : DockerClient

  def searchByTag(s: String): Image => Boolean = { img: Image =>
    val tags : Array[String] = Option(img.getRepoTags).getOrElse(Array.empty)
    val matched = tags.exists(_.matches(s))

    if (matched)
      logger.info(s"Image [$img] matches [$s]")

    matched
  }

  def images : List[Image] = client.listImagesCmd().exec().asScala.toList
    
  def running : List[Container] = client.listContainersCmd().exec().asScala.toList

  def search(f : Image => Boolean) = {
    val li = images
    li.filter(f)
  }

}
