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
import blended.updater.config.RuntimeConfig
import blended.updater.config.Artifact
import blended.updater.config.BundleConfig
import blended.updater.config.FeatureConfig
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.RootJsonFormat

/**
 * Defines type-classes to de-/serialization of protocol classes.
 */
trait JsonProtocol extends DefaultJsonProtocol {

  implicit val serviceInfoFormat: RootJsonFormat[ServiceInfo] = jsonFormat4(ServiceInfo)
  implicit val containerInfoFormat: RootJsonFormat[ContainerInfo] = jsonFormat3(ContainerInfo)
  implicit val artifactFormat: RootJsonFormat[Artifact] = jsonFormat3(Artifact)
  implicit val bundleConfigFormat: RootJsonFormat[BundleConfig] = jsonFormat3(BundleConfig)
  implicit val featureConfigFormat: RootJsonFormat[FeatureConfig] = jsonFormat5(FeatureConfig)
  implicit val runtimeConfigFormat: RootJsonFormat[RuntimeConfig] =
    // RuntimeConfig has an additional derived val confuses automatic field extraction 
    jsonFormat(RuntimeConfig,
      "name", "version", "bundles",
      "startLevel", "defaultStartLevel",
      "properties", "frameworkProperties", "systemProperties",
      "features", "resources")

  implicit val stageProfileFormat: RootJsonFormat[StageProfile] = jsonFormat1(StageProfile)
  implicit val activateProfileFormat: RootJsonFormat[ActivateProfile] = jsonFormat2(ActivateProfile)
  implicit val updateActionFormat: RootJsonFormat[UpdateAction] = new RootJsonFormat[UpdateAction] {
    import spray.json._
    override def write(obj: UpdateAction): JsValue = obj match {
      case s: StageProfile => s.toJson
      case a: ActivateProfile => a.toJson
      case _ => serializationError(s"Could not write object $obj")
    }
    override def read(json: JsValue): UpdateAction = {
      val fields = json.asJsObject.fields.keySet

      if (fields.contains("runtimeConfig")) stageProfileFormat.read(json)
      else if (fields.contains("profileName")) activateProfileFormat.read(json)
      else deserializationError("UpdateAction expected")
    }
  }

  implicit val responseFormat: RootJsonFormat[ContainerRegistryResponseOK] = jsonFormat2(ContainerRegistryResponseOK)
}

object JsonProtocol extends JsonProtocol
