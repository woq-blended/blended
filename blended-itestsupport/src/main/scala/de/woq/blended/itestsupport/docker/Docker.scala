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

package de.woq.blended.itestsupport.docker

import java.io.File
import akka.event.LoggingAdapter
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.core.{DockerClientBuilder, DockerClientConfig}
import com.typesafe.config.Config
import scala.collection.convert.Wrappers.JListWrapper
import scala.collection.convert.Wrappers.JListWrapper
import com.github.dockerjava.api.model.Container

object DockerClientFactory {

  var client : Option[DockerClient] = None
  
  def apply(config : Config)(implicit logger: LoggingAdapter) = client match {
    case Some(dockerClient) => dockerClient
    case _ =>
      logger.info(s"$config")
      val dockerHost = config.getString("docker.host")
      val dockerPort = config.getString("docker.port")

      val dockerConfig =  DockerClientConfig.createDefaultConfigBuilder()
        .withUri(s"http://$dockerHost:$dockerPort")
        .withUsername(config.getString("docker.user"))
        .withPassword(config.getString("docker.password"))
        .withEmail(config.getString("docker.eMail"))
        .build()

      logger info s"Trying to connect to docker at [${dockerConfig.getUri}]"

      val dockerClient = DockerClientBuilder.getInstance(dockerConfig).build()

      val version = dockerClient.versionCmd().exec()

      logger info
        s"""
       Using Docker version  ${version.getVersion}
       Docker API version    ${version.getApiVersion}
       Docker Go version     ${version.getGoVersion}
       Architecture          ${version.getArch}
       Kernel version        ${version.getKernelVersion}"""

      client = Some(dockerClient)
      dockerClient.asInstanceOf[DockerClient]
    }
}

trait VolumeBaseDir {

  val volumeBaseDir = {
    val dirName = System.getProperty("user.dir") + "/target/volumes"
    new File(dirName).mkdirs()
    dirName
  }
}

trait Docker { this: VolumeBaseDir =>

  implicit val logger  : LoggingAdapter
  implicit val config  : Config
  implicit val client  : DockerClient

  def searchByTag(s: String) = { img: Image =>
    img.getRepoTags.exists(_ matches s)
  }

  def images : List[Image] =
    JListWrapper(client.listImagesCmd().exec()).toList
    
  def running : List[Container] =
    JListWrapper(client.listContainersCmd().exec()).toList

  def search(f : Image => Boolean) =
    images.filter(f)

}
