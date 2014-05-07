/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.akka.mgmt.rest.internal

import org.scalatest.{Matchers, WordSpec}
import spray.testkit.ScalatestRouteTest
import de.woq.osgi.java.container.registry.{ContainerRegistryResponseOK, ContainerInfo}
import spray.httpx.SprayJsonSupport
import akka.actor.Props
import de.woq.osgi.java.container.registry.internal.ContainerRegistryImpl
import org.scalatest.mock.MockitoSugar
import org.osgi.framework.BundleContext

class ManagementCollectorSpec
  extends WordSpec
  with Matchers
  with MockitoSugar
  with ScalatestRouteTest
  with CollectorService
  with SprayJsonSupport
  with ContainerRegistryProvider {

  import de.woq.osgi.java.container.registry.ContainerRegistryJson._

  "The Management collector" should {

    "handle a posted container info" in {
      Post("/container", ContainerInfo("uuid", Map())) ~> collectorRoute ~> check {
        responseAs[ContainerRegistryResponseOK].id should be("uuid")
      }
    }
  }

  override def registry = {
    implicit val bc = mock[BundleContext]
    system.actorOf(Props(ContainerRegistryImpl()))
  }

  override implicit def actorRefFactory = system
}
