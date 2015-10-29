package blended.mgmt.base.json

import blended.mgmt.base.ServiceInfo
import blended.mgmt.base.ContainerInfo
import blended.updater.config.RuntimeConfig
import blended.updater.config.Artifact
import blended.updater.config.BundleConfig
import blended.updater.config.FeatureConfig
import spray.json.DefaultJsonProtocol
import spray.json.JsValue
import spray.json.RootJsonFormat
import blended.mgmt.base.StageProfile
import blended.mgmt.base.ActivateProfile
import blended.mgmt.base.UpdateAction
import blended.mgmt.base.ContainerRegistryResponseOK
import blended.updater.config.FeatureRef

/**
 * Defines type-classes to de-/serialization of protocol classes.
 */
trait JsonProtocol extends DefaultJsonProtocol {

  implicit val serviceInfoFormat: RootJsonFormat[ServiceInfo] = jsonFormat4(ServiceInfo)
  implicit val containerInfoFormat: RootJsonFormat[ContainerInfo] = jsonFormat3(ContainerInfo)
  implicit val artifactFormat: RootJsonFormat[Artifact] = jsonFormat3(Artifact)
  implicit val bundleConfigFormat: RootJsonFormat[BundleConfig] = jsonFormat3(BundleConfig)
  implicit val featureRefFormat: RootJsonFormat[FeatureRef] = jsonFormat3(FeatureRef)
  implicit val featureConfigFormat: RootJsonFormat[FeatureConfig] = jsonFormat5(FeatureConfig)
  implicit val runtimeConfigFormat: RootJsonFormat[RuntimeConfig] =
    // RuntimeConfig has an additional derived val confuses automatic field extraction 
    jsonFormat(RuntimeConfig,
      "name", "version", "bundles",
      "startLevel", "defaultStartLevel",
      "properties", "frameworkProperties", "systemProperties",
      "features", "resources", "resolvedFeatures")

  implicit val stageProfileFormat: RootJsonFormat[StageProfile] = jsonFormat1(StageProfile)
  implicit val activateProfileFormat: RootJsonFormat[ActivateProfile] = jsonFormat2(ActivateProfile)
  implicit val updateActionFormat: RootJsonFormat[UpdateAction] = new RootJsonFormat[UpdateAction] {
    import spray.json._
    override def write(obj: UpdateAction): JsValue = obj match {
      case s: StageProfile => s.toJson
      case a: ActivateProfile => a.toJson
      case _ => serializationError(s"Could not write object ${obj}")
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
