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

package blended.mgmt.rest.internal

import akka.testkit.TestLatch
import blended.mgmt.base.RemoteContainerState
import blended.mgmt.base.json._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import spray.httpx.SprayJsonSupport
import spray.testkit.ScalatestRouteTest
import blended.mgmt.base.ContainerInfo
import blended.mgmt.base.ContainerRegistryResponseOK

import scala.collection.immutable.Seq

class ManagementCollectorSpec
  extends WordSpec
    with Matchers
    with MockitoSugar
    with ScalatestRouteTest
    with CollectorService
    with SprayJsonSupport {

  val testPostLatch = TestLatch(1)
  val testGetLatch = TestLatch(1)

  "The Management collector" should {

    "handle a posted container info" in {
      Post("/container", ContainerInfo("uuid", Map("foo" -> "bar"), List())) ~> collectorRoute ~> check {
        responseAs[ContainerRegistryResponseOK].id should be("uuid")
      }
      testPostLatch.isOpen should be(true)
    }

    "handle a GET for container info" in {
      Get("/container") ~> infoRoute ~> check {
        responseAs[Seq[RemoteContainerState]] should be(Seq(RemoteContainerState(ContainerInfo("uuid", Map("foo" -> "bar"), List()), List())))
      }
      testGetLatch.isOpen should be(true)
    }

  }

  override implicit def actorRefFactory = system

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    testPostLatch.countDown()
    ContainerRegistryResponseOK(info.containerId)
  }

  override def getCurrentState(): Seq[RemoteContainerState] = {
    testGetLatch.countDown()
    List(RemoteContainerState(ContainerInfo("uuid", Map("foo" -> "bar"), List()), List()))
  }
}
