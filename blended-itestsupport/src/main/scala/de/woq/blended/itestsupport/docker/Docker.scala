package de.woq.blended.itestsupport.docker

import akka.event.LoggingAdapter
import com.github.dockerjava.client.model.Image

import scala.collection.convert.Wrappers.JListWrapper
import com.github.dockerjava.client.DockerClient
import com.typesafe.config.Config

import scala.collection.mutable

object DockerClientFactory {

  var client : Option[DockerClient] = None
  
  def apply(config : Config)(implicit logger: LoggingAdapter) = client match {
    case Some(dockerClient) => dockerClient
    case _ => {
      val dockerClient = new DockerClient(config.getString("docker.url"))
      dockerClient.setCredentials(
        config.getString("docker.user"),
        config.getString("docker.password"),
        config.getString("docker.eMail")
      )

      val version = dockerClient.versionCmd().exec()

      logger info
        s"""
       Using Docker version  ${version.getVersion}
       Docker API version    ${version.getApiVersion}
       Docker Go version     ${version.getGoVersion}
       Architecture          ${version.getArch}
       Kernel version        ${version.getKernelVersion}"""

      client = Some(dockerClient)
      dockerClient
    }
  }
}

trait Docker {

  implicit val logger  : LoggingAdapter
  implicit val config  : Config
  implicit val client  : DockerClient

  lazy val configuredContainers : Map[String, DockerContainer] = {

    val builder =
      new mutable.MapBuilder[String, DockerContainer, Map[String, DockerContainer]](Map.empty)

    val containerConfigs = new JListWrapper(config.getConfigList("docker.containers")).toList

    containerConfigs.foreach { cfg =>
      var idx  = 0

      logger info s"Analyzing container configuration ..."

      val images = search(searchByTag(cfg.getString("image")))
      logger info s"Found [${images.length}] image(s) for container definition."

      val links : List[String] =
        if (cfg.hasPath("links"))
          new JListWrapper(cfg.getStringList("links")).toList
        else
          List.empty

      logger info s"Container is linked to [${links.toString}]."

      images.foreach { img =>
        val name = if (cfg.hasPath("name")) cfg.getString("name") + "_" + idx else img.getId
        val ct = container(img, name).withNamedPorts(namedPorts(cfg))

        links.foreach(link => ct.withLink(link))

        builder += (name -> ct)
        logger info s"Configured container [${name}]."
        idx += 1
      }
    }

    val result = builder.result().toMap
    result
  }

  private[Docker] def namedPorts(cfg: Config) : Seq[NamedContainerPort] = {
    logger debug "Reading named ports for container..."

    var result : List[NamedContainerPort] = List.empty

    if (cfg.hasPath("ports")) {
      new JListWrapper(cfg.getConfigList("ports")).toList foreach { cfg =>
        val name = cfg.getString("name")
        val port = cfg.getInt("value")
        result = new NamedContainerPort(name, port) :: result
      }
    }

    logger info s"Found named ports for container [${result}]"
    result.seq
  }

  def exitingContainers() =
    new JListWrapper(client.listContainersCmd().withShowAll(true).exec()).toList

  def shutDownContainers() = {
    exitingContainers().foreach { ct =>
      logger.info(s"Removing container [${ct.getId}]")
      client.removeContainerCmd(ct.getId).withRemoveVolumes(true).withForce(true).exec()
    }
  }

  private[Docker] def searchByTag(s: String) = { img: Image =>
    img.getRepoTags.exists(_ matches(s))
  }

  private[Docker] def images =
    new JListWrapper(client.listImagesCmd().exec()).toList

  private[Docker] def search(f : Image => Boolean) =
    images.filter(f)

  private[Docker] def container(i : Image, name: String)  =
    new DockerContainer(i.getId, name)

}
