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

import akka.actor.{Terminated, Props}
import akka.testkit.TestActorRef
import de.wayofquality.blended.itestsupport.docker.protocol._
import de.wayofquality.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{DoNotDiscover, Matchers, WordSpecLike}
import scala.concurrent.duration._
import de.wayofquality.blended.itestsupport.{ContainerLink, ContainerUnderTest}
import java.util.UUID

class DependentContainerActorSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  "the DependentContainerActor" should {

    "Respond with a DependenciesStarted message after the last dependant container was started" in {
      
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

      watch(depActor)

      depActor ! ContainerStarted(Right(cut.copy(ctName = "blended_demo_0")))
      expectMsg( DependenciesStarted(Right(cut.copy(links = List(ContainerLink("foobar", "blended_demo"))))) )

      fishForMessage() {
        case m : Terminated => true
        case _ => false
      }
    }

  }

}
