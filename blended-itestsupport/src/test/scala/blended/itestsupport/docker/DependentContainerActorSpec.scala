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

import java.util.UUID

import akka.actor.{Props, Terminated}
import akka.testkit.{TestProbe, TestActorRef}
import blended.itestsupport.docker.protocol._
import blended.itestsupport.{ContainerLink, ContainerUnderTest}
import blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class DependentContainerActorSpec extends WordSpec
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  "the DependentContainerActor" should {

    "Respond with a DependenciesStarted message after the last dependant container was started" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val ctName = "blended_demo_0"
      val imageId = ctImageNames(ctName)
      
      val cut = ContainerUnderTest(
        ctName = "foo", 
        imgPattern = "^atooni/bar:latest",
        dockerName = "foobar",
        imgId = UUID.randomUUID().toString,
        links = List(ContainerLink("blended_demo_0", "blended_demo"))
      )
      
      val container = new DockerContainer(cut)
      val depActor = TestActorRef(Props(DependentContainerActor(cut)))

      probe.watch(depActor)

      depActor.tell(ContainerStarted(Right(cut.copy(ctName = "blended_demo_0"))), probe.ref)
      probe.expectMsg( DependenciesStarted(Right(cut.copy(links = List(ContainerLink("foobar", "blended_demo"))))) )

      probe.fishForMessage() {
        case m : Terminated => true
        case _ => false
      }
    }

  }

}
