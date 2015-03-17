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

import akka.actor.{Terminated, Props}
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{DoNotDiscover, Matchers, WordSpecLike}
import scala.concurrent.duration._

@DoNotDiscover
class DependentContainerActorSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  "the DependentContainerActor" should {

    "Respond with a DependenciesStarted message after the last dependant container was started" in {
//      val container = new DockerContainer(imageId, ctNames(0)).withLink("blended_demo_0:demo")
//      val depActor = TestActorRef(Props(DependentContainerActor(container)))
//
//      watch(depActor)
//
//      depActor ! ContainerStarted(Right("blended_demo_0"))
//      expectMsg( DependenciesStarted(Right(container)) )
//
//      fishForMessage(3.seconds) {
//        case m : Terminated => true
//        case _ => false
//      }
    }

  }

}
