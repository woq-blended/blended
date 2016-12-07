package blended.itestsupport.docker

import java.io.File

import akka.event.LoggingAdapter
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.{Container, Image}
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder}
import com.typesafe.config.Config

import scala.collection.convert.Wrappers.JListWrapper

object DockerClientFactory {

  var client : Option[DockerClient] = None
  
  def apply(config : Config)(implicit logger: LoggingAdapter) = client match {
    case Some(dockerClient) => dockerClient
    case _ =>
      logger.info(s"$config")

      val dockerConfig =  DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost("tcp://" + config.getString("docker.host") + ":" + config.getString("docker.port"))
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

trait VolumeBaseDir {

  val volumeBaseDir = {
    val dirName = System.getProperty("user.dir") + "/target/volumes"
    new File(dirName).mkdirs()
    dirName
  }
}

trait Docker { this: VolumeBaseDir =>

  implicit val logger  : LoggingAdapter
  implicit val config  : Config
  implicit val client  : DockerClient

  def searchByTag(s: String) = { img: Image =>
    img.getRepoTags.exists(_ matches s)
  }

  def images : List[Image] =
    JListWrapper(client.listImagesCmd().exec()).toList
    
  def running : List[Container] =
    JListWrapper(client.listContainersCmd().exec()).toList

  def search(f : Image => Boolean) =
    images.filter(f)

}
