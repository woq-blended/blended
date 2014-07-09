package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.model.Image
import de.woq.blended.itestsupport.PortScanner
import org.slf4j.LoggerFactory

import scala.collection.convert.Wrappers.JListWrapper
import com.github.dockerjava.client.DockerClient
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable

object Docker {

  private[Docker] var currentMinPort : Int = 1024

  private[Docker] lazy val logger = LoggerFactory.getLogger(Docker.getClass)

  private[Docker] lazy val config = {
    ConfigFactory.parseResources("docker.conf")
  }

  implicit private[Docker] lazy val client = {
    val client = new DockerClient(config.getString("docker.url"))
    client.setCredentials(
      config.getString("docker.user"),
      config.getString("docker.password"),
      config.getString("docker.eMail")
    )

    val version = client.versionCmd().exec()

    logger info
      s"""
         Using Docker version  ${version.getVersion}
         Docker API version    ${version.getApiVersion}
         Docker Go version     ${version.getGoVersion}
         Architecture          ${version.getArch}
         Kernel version        ${version.getKernelVersion}"""

    client
  }

  def searchByTag(s: String) = { img: Image =>
    img.getRepoTags.exists(_ matches(s))
  }

  def images = new JListWrapper(client.listImagesCmd().exec()).toList
  def search(f : Image => Boolean) = images.filter(f)

  lazy val configuredContainers : Map[String, DockerContainer] = {

    val builder =
      new mutable.MapBuilder[String, DockerContainer, Map[String, DockerContainer]](Map.empty)

    val containerConfigs = new JListWrapper(config.getConfigList("docker.containers")).toList

    containerConfigs.foreach { cfg =>
      var idx  = 0

      logger info s"Analyzing container configuration ..."

      val images = search(searchByTag(cfg.getString("image")))
      logger info s"Found [${images.length}] image(s) for container definition."

      images.foreach { img =>
        val name = if (cfg.hasPath("name")) cfg.getString("name") + "_" + idx else img.getId
        val ct = container(img).withName(name).withNamedPorts(namedPorts(cfg))

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

  def container(i : Image)  = new DockerContainer(i.getId)
  def container(s : String) = new DockerContainer(s)

  def nextFreePort = {
    val port = PortScanner.findFreePort(currentMinPort)
    currentMinPort = port + 1
    port
  }
}
