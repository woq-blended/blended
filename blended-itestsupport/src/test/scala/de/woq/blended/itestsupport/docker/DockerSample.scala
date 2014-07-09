package de.woq.blended.itestsupport.docker

object DockerSample extends App with Docker {

  configuredContainers.values foreach { c =>
    c.startContainer.waitContainer.stopContainer
  }

}
