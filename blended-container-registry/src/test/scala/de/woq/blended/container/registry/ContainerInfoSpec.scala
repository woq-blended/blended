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

package de.woq.blended.karaf.container.registry

import org.scalatest.{Matchers, WordSpec}
import org.slf4j.LoggerFactory
import de.woq.blended.persistence.protocol._
import de.woq.blended.container.registry.protocol._

class ContainerInfoSpec extends WordSpec with Matchers {

  val log = LoggerFactory.getLogger(classOf[ContainerInfoSpec])

  import spray.json._

  "ContainerInfo" should {

    "serialize to Json correctly" in {
      val info = ContainerInfo("uuid", Map("fooo" -> "bar"))
      val json = info.toJson
      json.compactPrint should be("""{"containerId":"uuid","properties":{"fooo":"bar"}}""")
    }

    "serialize from Json correctly" in {
      val json = """{"containerId":"uuid","properties":{"fooo":"bar"}}""".parseJson
      val info = json.convertTo[ContainerInfo]

      info should be(ContainerInfo("uuid", Map("fooo" -> "bar")))
    }

    "create the Persistence Properties correctly" in {

      import DataObject._

      val info = ContainerInfo("uuid", Map("fooo" -> "bar"))

      val props = info.persistenceProperties

      props._1 should be (info.getClass.getSimpleName)
      props._2.size should be (2)
      props._2(PROP_UUID) should be (PersistenceProperty[String]("uuid"))
      props._2("fooo") should be (PersistenceProperty[String]("bar"))
    }
  }
}
