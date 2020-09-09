package blended.itestsupport.docker

import com.github.dockerjava.api.DockerClient

trait DockerClientProvider {
  def getClient : DockerClient
}
