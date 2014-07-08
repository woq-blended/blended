package de.woq.blended.itestsupport.docker

object DockerSample extends App {

  val docker = new Docker

  val images = docker.search(docker.searchByTag("^blended-docker-demo.*")).toList

  val container = docker.container(images.head.getId)

  container
    .withNamedPorts(
      new NamedContainerPort("http", 8181),
      new NamedContainerPort("jmx", 1099)
    ).startContainer
  container.waitContainer

  println(container.port("http"))
  println(container.port("jmx"))

  container.stopContainer

}
