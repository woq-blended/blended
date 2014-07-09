package de.woq.blended.itestsupport.docker

object DockerSample extends App {

  val container = Docker.configuredContainers

  container.values foreach { c =>
    c.startContainer.waitContainer.stopContainer
  }

}
