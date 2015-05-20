/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.itestsupport.docker

import akka.actor.Props
import akka.testkit.TestActorRef
import blended.itestsupport.ContainerUnderTest
import blended.itestsupport.docker.protocol._
import blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.mock.MockitoSugar

class ContainerManagerSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {
  
  private[this] val log = system.log

  object TestContainerManager {
    def apply() = new  ContainerManager with DockerClientProvider {
      override def getClient = mockClient
    }
  }

  "The ContainerManager" should {

    "Respond with an event after all containers have been started" in {
      
      val cuts = ContainerUnderTest.containerMap(system.settings.config)
      
      log.info(s"$cuts")
      
      val mgr = TestActorRef(Props(TestContainerManager()), "mgr")
      mgr ! StartContainerManager(cuts)
      
      expectMsgType[ContainerManagerStarted]
    }
  }
}
