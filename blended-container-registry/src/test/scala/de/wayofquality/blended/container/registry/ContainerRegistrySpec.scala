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

package de.wayofquality.blended.karaf.container.registry

import akka.actor.Props
import akka.testkit.TestActorRef
import de.wayofquality.blended.container.registry.internal.ContainerRegistryImpl
import de.wayofquality.blended.testsupport.TestActorSys
import org.osgi.framework.BundleContext
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import de.wayofquality.blended.container.registry.protocol._

class ContainerRegistrySpec extends WordSpec with MockitoSugar with Matchers {

  "Container registry" should {

    "Respond with a OK message upon an container update message" in new TestActorSys {
      implicit val osgiContext = mock[BundleContext]

      val registry = TestActorRef(Props(ContainerRegistryImpl()))
      registry ! UpdateContainerInfo(ContainerInfo("foo", Map("name" -> "andreas")))

      expectMsg(ContainerRegistryResponseOK("foo"))
    }
  }

}
