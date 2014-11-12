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

import akka.event.LoggingAdapter
import com.typesafe.config.Config
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

class DockerSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  private def docker = {
    System.setProperty("docker.io.version", "1.12")
    new Docker with VolumeBaseDir {
      override implicit val logger: LoggingAdapter = system.log
      override implicit val config: Config = system.settings.config
      override implicit val client = mockClient
    }
  }

  "The Docker trait" should {

    "Read initialize from the configuration" in {
      docker.configuredContainers should have size(ctNames.size)

      ctNames.foreach { name =>
        val container = docker.configuredContainers.get(name).get
        container.containerName should be (name)
      }
    }
  }
}
