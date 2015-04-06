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

import com.github.dockerjava.api.model.Link
import de.wayofquality.blended.itestsupport.NamedContainerPort
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{DoNotDiscover, Matchers, WordSpec}
import org.slf4j.LoggerFactory
import de.wayofquality.blended.itestsupport.ContainerUnderTest

class DockerContainerSpec extends WordSpec
  with Matchers
  with DockerTestSetup
  with MockitoSugar {
  
  private[this] val log = LoggerFactory.getLogger(classOf[DockerContainerSpec])

  "A Docker Container should" should {

    val cuts = ContainerUnderTest.containerMap(config)
    val cut = cuts("blended_demo")
    
    log.info(s"$cut")

    "be created from the image id and a name" in {
      val container = new DockerContainer(cut)
    }

    "issue the stop command with the correct id" in {
      val container = new DockerContainer(cut)
      container.stopContainer

      verify(mockClient).stopContainerCmd(cut.dockerName)
    }

    "issue the start command with the correct id" in {
      val container = new DockerContainer(cut)
      container.startContainer

      verify(mockClient).startContainerCmd(cut.dockerName)
      val createCmd = mockClient.createContainerCmd(cut.imgId)
      verify(createCmd).withName(cut.dockerName)
    }

    "issue the InspectContainerCommand with the correct id" in {
      val container = new DockerContainer(cut)
      container.containerInfo

      verify(mockClient).inspectContainerCmd(cut.dockerName)
    }
  }
}
