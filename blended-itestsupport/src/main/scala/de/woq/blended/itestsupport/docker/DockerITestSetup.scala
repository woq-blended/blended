package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.model.Image

trait DockerImageResolver {

  def resolveImages : List[Image]
}

class DockerITestSetup { this : DockerImageResolver =>



}
