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

import blended.container.registry.protocol._
import blended.persistence.protocol._
import org.scalatest.{Matchers, WordSpec}
import org.slf4j.LoggerFactory
import blended.mgmt.base.ServiceInfo

class ContainerInfoSpec extends WordSpec with Matchers {

  val log = LoggerFactory.getLogger(classOf[ContainerInfoSpec])

  import spray.json._

  "ContainerInfo" should {

	  val serviceInfo = ServiceInfo("service", 1234567890L, 30000L, Map("prop1" -> "val1"))

    "serialize to Json correctly" in {
      val info = ContainerInfo("uuid", Map("fooo" -> "bar"), List(serviceInfo))
      val json = info.toJson
      json.compactPrint should be("""{"containerId":"uuid","properties":{"fooo":"bar"},"serviceInfos":[{"name":"service","timestampMsec":1234567890,"lifetimeMsec":30000,"props":{"prop1":"val1"}}]}""")
    }

    "serialize from Json correctly" in {
      val json = """{"containerId":"uuid","properties":{"fooo":"bar"},"serviceInfos":[{"name":"service","timestampMsec":1234567890,"lifetimeMsec":30000,"props":{"prop1":"val1"}}]}""".parseJson
      val info = json.convertTo[ContainerInfo]

      info should be(ContainerInfo("uuid", Map("fooo" -> "bar"), List(serviceInfo)))
    }

    "create the Persistence Properties correctly" in {

      val info = ContainerInfo("uuid", Map("fooo" -> "bar"), List())

      val props = info.persistenceProperties

      props._1 should be (info.getClass.getName.replaceAll("\\.", "_"))
      props._2.size should be (2)
      props._2(DataObject.PROP_UUID) should be (PersistenceProperty[String]("uuid"))
      props._2("fooo") should be (PersistenceProperty[String]("bar"))
    }
  }
}
