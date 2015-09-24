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

package blended.karaf.container.registry

import blended.persistence.protocol._
import org.scalatest.{ Matchers, WordSpec }
import org.slf4j.LoggerFactory
import blended.mgmt.base.ServiceInfo
import blended.updater.config.RuntimeConfig
import scala.collection.immutable
import blended.updater.config.BundleConfig
import blended.mgmt.base.ContainerInfo
import blended.mgmt.base.json._
import blended.mgmt.base.StageProfile
import blended.mgmt.base.ActivateProfile
import blended.mgmt.base.ContainerRegistryResponseOK

class ContainerInfoSpec extends WordSpec with Matchers {

  val log = LoggerFactory.getLogger(classOf[ContainerInfoSpec])

  import spray.json._

  "ContainerInfo" should {

    val serviceInfo = ServiceInfo("service", 1234567890L, 30000L, Map("prop1" -> "val1"))
    val containerInfo = ContainerInfo("uuid", Map("fooo" -> "bar"), List(serviceInfo))
    val expectedJson = """{"containerId":"uuid","properties":{"fooo":"bar"},"serviceInfos":[{"name":"service","timestampMsec":1234567890,"lifetimeMsec":30000,"props":{"prop1":"val1"}}]}"""

    "serialize to Json correctly" in {
      val json = containerInfo.toJson
      json.compactPrint should be(expectedJson)
    }

    "serialize from Json correctly" in {
      val json = expectedJson.parseJson
      val info = json.convertTo[ContainerInfo]

      info should be(containerInfo)
    }

    "create the Persistence Properties correctly" in {
      pending
//
//      val info = ContainerInfo("uuid", Map("fooo" -> "bar"), List())
//
//      val props = info.persistenceProperties
//
//      props._1 should be(info.getClass.getName.replaceAll("\\.", "_"))
//      props._2.size should be(2)
//      props._2(DataObject.PROP_UUID) should be(PersistenceProperty[String]("uuid"))
//      props._2("fooo") should be(PersistenceProperty[String]("bar"))
    }

  }

  "ContainerRegistryResponseOK" should {

    val stageAction = StageProfile(
      RuntimeConfig(name = "testprofile", version = "1", startLevel = 10, defaultStartLevel = 10, bundles = immutable.Seq(BundleConfig(url = "mvn:g:a:v", startLevel = 0))))
    val activateAction = ActivateProfile(profileName = "testprofile", profileVersion = "1")

    val response = ContainerRegistryResponseOK("uuid", immutable.Seq(stageAction, activateAction))

    "serialize and deserialize result in equal object" in {
      response should be(response.toJson.compactPrint.parseJson.convertTo[ContainerRegistryResponseOK])
    }

  }
}
