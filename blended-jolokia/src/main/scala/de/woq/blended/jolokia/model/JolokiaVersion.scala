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

package de.woq.blended.jolokia.model

import spray.json._
import DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._

object JolokiaVersion {
  def apply(value: JsValue) = {
    val agent    = value.extract[String]("value" / "protocol")
    val protocol = value.extract[String]("value" / "protocol")
    val config   = value.extract[Map[String,String]]("value" / "config")
    new JolokiaVersion(agent, protocol, config)
  }
}

case class JolokiaVersion(agent: String, protocol: String, config: Map[String,String])
