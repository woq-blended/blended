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

package blended.container.registry.protocol

import blended.mgmt.base.ServiceInfo
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

/**
 * Defines type-classes to de-/serialization of protocol classes.
 */
trait JsonProtocol extends DefaultJsonProtocol {
  implicit val serviceInfoFormat: RootJsonFormat[ServiceInfo] = jsonFormat4(ServiceInfo)
  implicit val containerInfoFormat: RootJsonFormat[ContainerInfo] = jsonFormat3(ContainerInfo)
  implicit val responseFormat: RootJsonFormat[ContainerRegistryResponseOK] = jsonFormat1(ContainerRegistryResponseOK)
}

object JsonProtocol extends JsonProtocol