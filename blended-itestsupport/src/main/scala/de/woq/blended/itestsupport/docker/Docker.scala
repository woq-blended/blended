package de.woq.blended.itestsupport.docker

import java.io.File

import akka.event.LoggingAdapter
import com.github.dockerjava.client.model.{Bind, Volume, Image}
import de.woq.blended.itestsupport.ShellExecutor
import org.apache.commons.io.FileUtils

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

  val volumeBaseDir = {
    val dirName = System.getProperty("user.dir") + "/target/volumes"
    new File(dirName).mkdirs()
    dirName
  }

  lazy val configuredContainers : Map[String, DockerContainer] = {

    val builder =
      new mutable.MapBuilder[String, DockerContainer, Map[String, DockerContainer]](Map.empty)

    val containerConfigs = new JListWrapper(config.getConfigList("docker.containers")).toList

    containerConfigs.foreach { cfg =>
      var idx  = 0

      logger info s"Analyzing container configuration ..."

      val images = search(searchByTag(cfg.getString("image")))
      logger info s"Found [${images.length}] image(s) for container definition."

      val ctLinks = links(cfg)
      logger info s"Container is linked to [${ctLinks.toString}]."

      val ctVolumes = volumes(cfg)
      logger info s"Container has volumes [${ctVolumes.toString}]."

      images.foreach { img =>
        val name = if (cfg.hasPath("name")) cfg.getString("name") + "_" + idx else img.getId
        val ct = container(img, name).withNamedPorts(namedPorts(cfg))


        ctLinks.foreach(link => ct.withLink(link))
        ctVolumes.foreach{volume =>
          val dirName = s"${volumeBaseDir}/${name}/${volume._1}"
          ct.withVolume(dirName, volume._2, volume._3)
          new File(dirName).mkdirs()
          ShellExecutor.excute(s"chmod -R 777 ${dirName}")
        }

        builder += (name -> ct)
        logger info s"Configured container [${name}]."
        idx += 1
      }
    }

    val result = builder.result().toMap
    result
  }

  private[Docker] def links(cfg: Config) : List[String] =
    if (cfg.hasPath("links"))
      new JListWrapper(cfg.getStringList("links")).toList
    else
      List.empty

  private[Docker] def volumes(cfg : Config) : List[(String, Volume, Boolean)] =
    if (cfg.hasPath("volumes")) {
      val volumesConfig = new JListWrapper(cfg.getConfigList("volumes")).toList

      volumesConfig.map { volumeConfig =>

        val hostDir = volumeConfig.getString("host")
        val ctDir = volumeConfig.getString("container")
        val ro = if (volumeConfig.hasPath("readonly"))
          volumeConfig.getBoolean("readonly")
        else
          false

        (hostDir, new Volume(ctDir), ro)
      }
    }
    else
      List.empty


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
