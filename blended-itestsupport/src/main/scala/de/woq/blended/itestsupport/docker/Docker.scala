package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.model.Image

import scala.collection.convert.Wrappers.JListWrapper
import com.github.dockerjava.client.DockerClient
import com.typesafe.config.ConfigFactory

class Docker {

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
    client
  }

  def searchByTag(s: String) = { img: Image =>
    img.getRepoTags.exists(_ matches(s))
  }

  def images = new JListWrapper(client.listImagesCmd().exec())

  def search(f : Image => Boolean) = images.filter(f)

  def container(s : String) = new DockerContainer(s)
}
