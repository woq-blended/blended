/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.wayofquality.blended.itestsupport.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model._
import de.wayofquality.blended.itestsupport.{ContainerUnderTest, NamedContainerPort}
import org.slf4j.LoggerFactory

/*
 * Provide a simple wrapper around the excellent docker Java API to create an abstraction suitable for
 * integration tests.
 */

class DockerContainer(cut: ContainerUnderTest)(implicit client: DockerClient) {

  var linkedContainers : List[Link] = List.empty
  var mappedVolumes : List[Bind] = List.empty
  var ports : Map[String, NamedContainerPort] = Map.empty

  private[this] val logger = LoggerFactory.getLogger(classOf[DockerContainer].getName)

  /**
   * @return The docker image id of the container.
   */
  def id = cut.imgId

  /**
   * @return The docker runtime name of the container.
   */
  def containerName = cut.dockerName

  /**
   * @return A list of runtime names that the container relies on in terms of docker links.
   */
  def links = linkedContainers

  def binds = mappedVolumes

  /**
   * Start the container with a given set of exposed ports. Exposed ports are defined in terms of docker
   * port mappings and map zero or more exposed container ports to physical ports within the hosting
   * OS. The mapping can be injected while starting the container as the port mapping is usually calculated
   * by some manager object that knows about available ports or can determine available ports upon request.
   */
  def startContainer = {
    logger info s"Starting container [${cut.dockerName}] with container links [$linkedContainers]."
    
    val container  = client.createContainerCmd(id).withName(cut.dockerName).withTty(true).exec()
    val cmd = client.startContainerCmd(containerName).withPublishAllPorts(true)
    if (!linkedContainers.isEmpty) cmd.withLinks(links:_*)
    
    cmd.exec()

    this
  }

  def containerInfo = client.inspectContainerCmd(containerName).exec()

  def removeContainer = {
    logger info s"Removing container [$containerName] from Docker."
    client.removeContainerCmd(containerName).withForce(true).withRemoveVolumes(true).exec()
  }
  
  def stopContainer = {
    logger info s"Stopping container [$containerName]"
    client.stopContainerCmd(containerName).exec()
    this
  }

  def withNamedPort(port: NamedContainerPort) = {
    this.ports += (port.name -> port)
    this
  }

  def withNamedPorts(ports : Seq[NamedContainerPort]) = {
    ports.foreach(withNamedPort)
    this
  }

  def withLink(link : String) = {
    this.linkedContainers = Link.parse(link) :: this.linkedContainers
    this
  }

  def withVolume(mappedDir : String, volume: Volume, rw: AccessMode) = {
    this.mappedVolumes = new Bind(mappedDir, volume, rw) :: mappedVolumes
    this
  }

}
