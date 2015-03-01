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

import akka.actor.Props
import akka.testkit.TestActorRef
import com.typesafe.config.Config
import de.woq.blended.itestsupport.ContainerUnderTest
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{WordSpecLike, Matchers}
import scala.collection.convert.Wrappers.JListWrapper
import scala.concurrent.duration._

import de.woq.blended.itestsupport.docker.protocol._

class ContainerManagerSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  object TestContainerManager {
    def apply() = new  EmbeddedContainerManager with DockerClientProvider {
      override def getClient = mockClient
    }
  }

  "The ContainerManager" should {

    "Respond with an event after all containers have been started" in {
      
      val cuts = JListWrapper(config.getConfigList("docker.containers")).map { cfg : Config =>
        ContainerUnderTest(cfg)
      }.toList
      
      val mgr = TestActorRef(Props(TestContainerManager()), "mgr")
      mgr ! StartContainerManager(cuts)
      
      fishForMessage() {
        case DockerContainerAvailable(cuts) => cuts match {
          case Right(l) => l.size == 2
          case _ => false
        }
        case _ => false
      }
    }
  }
}
