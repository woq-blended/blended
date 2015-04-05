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

package de.wayofquality.blended.mgmt.rest.internal

import org.scalatest.{Matchers, WordSpec}
import spray.testkit.ScalatestRouteTest
import spray.httpx.SprayJsonSupport
import org.scalatest.mock.MockitoSugar
import akka.testkit.TestLatch

import de.wayofquality.blended.container.registry.protocol._

class ManagementCollectorSpec
  extends WordSpec
  with Matchers
  with MockitoSugar
  with ScalatestRouteTest
  with CollectorService
  with SprayJsonSupport {

  val testLatch = TestLatch(1)

  "The Management collector" should {

    "handle a posted container info" in {
      Post("/container", ContainerInfo("uuid", Map("foo" -> "bar"))) ~> collectorRoute ~> check {
        responseAs[ContainerRegistryResponseOK].id should be("uuid")
      }
      testLatch.isOpen should be (true)
    }
  }

  override implicit def actorRefFactory = system

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    testLatch.countDown()
    ContainerRegistryResponseOK(info.containerId)
  }
}
